package org.koitharu.kotatsu.parsers

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.medianOrNull
import org.koitharu.kotatsu.parsers.util.mimeType
import org.koitharu.kotatsu.test_util.*
import kotlin.time.Duration.Companion.minutes

//@ExtendWith(AuthCheckExtension::class)
internal class MangaParserTest {

	private val context = MangaLoaderContextMock
	private val timeout = 2.minutes

	@ParameterizedTest(name = "{index}|list|{0}")
	@MangaSources
	fun list(source: MangaSource) = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val list = parser.getList(0, null)
		checkMangaList(list, "list")
		assert(list.all { it.source == source })
	}

	@ParameterizedTest(name = "{index}|pagination|{0}")
	@MangaSources
	fun pagination(source: MangaSource) = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val page1 = parser.getList(0, filter = null)
		val page2 = parser.getList(page1.size, filter = null)
		if (parser is PagedMangaParser) {
			assert(parser.pageSize >= page1.size) {
				"Page size is ${page1.size} but ${parser.pageSize} expected"
			}
		}
		assert(page1.isNotEmpty()) { "Page 1 is empty" }
		assert(page2.isNotEmpty()) { "Page 2 is empty" }
		assert(page1 != page2) { "Pages are equal" }
		val intersection = page1.intersect(page2.toSet())
		assert(intersection.isEmpty()) {
			"Pages are intersected by " + intersection.size
		}
	}

	@ParameterizedTest(name = "{index}|search|{0}")
	@MangaSources
	fun search(source: MangaSource) = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val subject = parser.getList(
			offset = 0,
			filter = MangaListFilter.Advanced(
				sortOrder = SortOrder.POPULARITY,
				tags = emptySet(),
				locale = null,
				states = emptySet(),
				tagsExclude = emptySet(),
				contentRating = emptySet(),
			),
		).minByOrNull {
			it.title.length
		} ?: error("No manga found")
		val query = subject.title
		check(query.isNotBlank()) { "Manga title '$query' is blank" }
		val list = parser.getList(0, MangaListFilter.Search(query))
		assert(list.isNotEmpty()) { "Empty search results by \"$query\"" }
		assert(list.singleOrNull { it.url == subject.url && it.id == subject.id } != null) {
			"Single subject '${subject.title} (${subject.publicUrl})' not found in search results"
		}
		checkMangaList(list, "search('$query')")
		assert(list.all { it.source == source })
	}

	@ParameterizedTest(name = "{index}|tags|{0}")
	@MangaSources
	fun tags(source: MangaSource) = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val tags = parser.getAvailableTags()
		assert(tags.isNotEmpty()) { "No tags found" }
		val keys = tags.map { it.key }
		assert(keys.isDistinct())
		assert("" !in keys)
		val titles = tags.map { it.title }
		assert(titles.isDistinct())
		assert("" !in titles)
		assert(titles.all { it.isCapitalized() }) {
			val badTags = titles.filterNot { it.isCapitalized() }.joinToString()
			"Not all tags are capitalized: $badTags"
		}
		assert(tags.all { it.source == source })

		val tag = tags.last()
		val list = parser.getList(
			offset = 0,
			MangaListFilter.Advanced.Builder(parser.defaultSortOrder)
				.tags(setOf(tag))
				.build(),
		)
		checkMangaList(list, "${tag.title} (${tag.key})")
		assert(list.all { it.source == source })
	}

	@ParameterizedTest(name = "{index}|tags_multiple|{0}")
	@MangaSources
	fun tagsMultiple(source: MangaSource) = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		if (!parser.isMultipleTagsSupported) return@runTest
		val tags = parser.getAvailableTags().shuffled().take(2).toSet()

		val filter = MangaListFilter.Advanced.Builder(parser.availableSortOrders.first())
			.tags(tags)
			.build()
		val list = parser.getList(0, filter)
		checkMangaList(list, "${tags.joinToString { it.title }} (${tags.joinToString { it.key }})")
		assert(list.all { it.source == source })
	}

	@ParameterizedTest(name = "{index}|locale|{0}")
	@MangaSources
	fun locale(source: MangaSource) = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val locales = parser.getAvailableLocales()
		if (locales.isEmpty()) {
			return@runTest
		}
		val filter = MangaListFilter.Advanced(
			sortOrder = parser.availableSortOrders.first(),
			tags = setOf(),
			tagsExclude = setOf(),
			locale = locales.random(),
			states = setOf(),
			contentRating = setOf(),
		)
		val list = parser.getList(offset = 0, filter)
		checkMangaList(list, filter.locale.toString())
		assert(list.all { it.source == source })
	}


	@ParameterizedTest(name = "{index}|details|{0}")
	@MangaSources
	fun details(source: MangaSource) = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val list = parser.getList(0, null)
		val manga = list[3]
		parser.getDetails(manga).apply {
			assert(!chapters.isNullOrEmpty()) { "Chapters are null or empty" }
			assert(publicUrl.isUrlAbsolute()) { "Manga public url is not absolute: '$publicUrl'" }
			assert(description != null) { "Detailed description is null: '$publicUrl'" }
			assert(title.startsWith(manga.title)) {
				"Titles are mismatch: '$title' and '${manga.title}' for $publicUrl"
			}
			assert(this.source == source)
			val c = checkNotNull(chapters)
			assert(c.isDistinctBy { it.id }) {
				"Chapters are not distinct by id: ${c.maxDuplicates { it.id }} for $publicUrl"
			}
			assert(c.isDistinctByNotNull { it.key() }) {
				val dup = c.mapNotNull { it.key() }.maxDuplicates { it }
				"Chapters are not distinct by branch/volume/number: $dup for $publicUrl"
			}
			assert(c.all { it.source == source })
			checkImageRequest(coverUrl, source)
			largeCoverUrl?.let {
				checkImageRequest(it, source)
			}
		}
	}

	@ParameterizedTest(name = "{index}|pages|{0}")
	@MangaSources
	fun pages(source: MangaSource) = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val list = parser.getList(0, null)
		val manga = list.first()
		val chapter = parser.getDetails(manga).chapters?.firstOrNull() ?: error("Chapter is null at ${manga.publicUrl}")
		val pages = parser.getPages(chapter)

		assert(pages.isNotEmpty())
		assert(pages.isDistinctBy { it.id })
		assert(pages.all { it.source == source })

		arrayOf(
			pages.first(),
			pages.medianOrNull() ?: error("No page"),
		).forEach { page ->
			val pageUrl = parser.getPageUrl(page)
			assert(pageUrl.isNotEmpty())
			assert(pageUrl.isUrlAbsolute())
			checkImageRequest(pageUrl, page.source)
		}
	}

	@ParameterizedTest(name = "{index}|favicon|{0}")
	@MangaSources
	fun favicon(source: MangaSource) = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val favicons = parser.getFavicons()
		val types = setOf("png", "svg", "ico", "gif", "jpg", "jpeg")
		assert(favicons.isNotEmpty())
		favicons.forEach {
			assert(it.url.isUrlAbsolute()) { "Favicon url is not absolute: ${it.url}" }
			assert(it.type in types) { "Unknown icon type: ${it.type}" }
		}
		val favicon = favicons.find(24)
		checkNotNull(favicon)
		checkImageRequest(favicon.url, source)
	}

	@ParameterizedTest(name = "{index}|domain|{0}")
	@MangaSources
	fun domain(source: MangaSource) = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val defaultDomain = parser.domain
		val url = HttpUrl.Builder().host(defaultDomain).scheme("https").toString()
		val response = context.doRequest(url, source)
		val realUrl = response.request.url
		val realDomain = realUrl.topPrivateDomain()
		val realHost = realUrl.host
		assert(defaultDomain == realHost || defaultDomain == realDomain) {
			"Domain mismatch:\nRequired:\t\t\t$defaultDomain\nActual:\t\t\t$realDomain\nHost:\t\t\t$realHost"
		}
	}

	@ParameterizedTest(name = "{index}|authorization|{0}")
	@MangaSources
	@Disabled
	fun authorization(source: MangaSource) = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		if (parser is MangaParserAuthProvider) {
			val username = parser.getUsername()
			assert(username.isNotBlank()) { "Username is blank" }
			println("Signed in to ${source.name} as $username")
		}
	}

	private suspend fun checkMangaList(list: List<Manga>, cause: String) {
		assert(list.isNotEmpty()) { "Manga list for '$cause' is empty" }
		assert(list.isDistinctBy { it.id }) { "Manga list for '$cause' contains duplicated ids" }
		for (item in list) {
			assert(item.url.isNotEmpty()) { "Url is empty" }
			assert(!item.url.isUrlAbsolute()) { "Url looks like absolute: ${item.url}" }
			assert(item.coverUrl.isUrlAbsolute()) { "Cover url is not absolute: ${item.coverUrl}" }
			assert(item.title.isNotEmpty()) { "Title for ${item.publicUrl} is empty" }
			assert(item.publicUrl.isUrlAbsolute())
		}
		val testItem = list.random()
		checkImageRequest(testItem.coverUrl, testItem.source)
	}

	private suspend fun checkImageRequest(url: String, source: MangaSource) {
		context.doRequest(url, source).use {
			assert(it.isSuccessful) { "Request failed: ${it.code}(${it.message}): $url" }
			assert(it.mimeType?.startsWith("image/") == true) {
				"Wrong response mime type: ${it.mimeType}"
			}
		}
	}

	private fun String.isCapitalized(): Boolean {
		return !first().isLowerCase()
	}

	private fun MangaChapter.key(): Any? = when {
		number > 0f && volume > 0 -> Triple(branch, volume, number)
		number > 0f -> Pair(branch, number)
		else -> null
	}
}
