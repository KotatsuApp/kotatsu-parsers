package org.koitharu.kotatsu.parsers

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.medianOrNull
import org.koitharu.kotatsu.parsers.util.mimeType
import org.koitharu.kotatsu.test_util.isDistinct
import org.koitharu.kotatsu.test_util.isDistinctBy
import org.koitharu.kotatsu.test_util.isUrlAbsolute
import org.koitharu.kotatsu.test_util.maxDuplicates


@ExtendWith(AuthCheckExtension::class)
internal class MangaParserTest {

	private val context = MangaLoaderContextMock()

	@ParameterizedTest(name = "{index}|list|{0}")
	@MangaSources
	fun list(source: MangaSource) = runTest {
		val parser = source.newParser(context)
		val list = parser.getList(20, sortOrder = SortOrder.POPULARITY, tags = null)
		checkMangaList(list, "list")
		assert(list.all { it.source == source })
	}

	@ParameterizedTest(name = "{index}|search|{0}")
	@MangaSources
	fun search(source: MangaSource) = runTest {
		val parser = source.newParser(context)
		val subject = parser.getList(20, sortOrder = SortOrder.POPULARITY, tags = null).minByOrNull {
			it.title.length
		} ?: error("No manga found")
		val query = subject.title
		check(query.isNotBlank()) { "Manga title '$query' is blank" }
		val list = parser.getList(0, query)
		assert(list.singleOrNull { it.url == subject.url && it.id == subject.id } != null) {
			"Single subject '${subject.title} (${subject.publicUrl})' not found in search results"
		}
		checkMangaList(list, "search('$query')")
		assert(list.all { it.source == source })
	}

	@ParameterizedTest(name = "{index}|tags|{0}")
	@MangaSources
	fun tags(source: MangaSource) = runTest {
		val parser = source.newParser(context)
		val tags = parser.getTags()
		assert(tags.isNotEmpty()) { "No tags found" }
		val keys = tags.map { it.key }
		assert(keys.isDistinct())
		assert("" !in keys)
		val titles = tags.map { it.title }
		assert(titles.isDistinct())
		assert("" !in titles)
		assert(tags.all { it.source == source })

		val tag = tags.last()
		val list = parser.getList(offset = 0, tags = setOf(tag), sortOrder = null)
		checkMangaList(list, "${tag.title} (${tag.key})")
		assert(list.all { it.source == source })
	}

	@ParameterizedTest(name = "{index}|details|{0}")
	@MangaSources
	fun details(source: MangaSource) = runTest {
		val parser = source.newParser(context)
		val list = parser.getList(20, sortOrder = SortOrder.POPULARITY, tags = null)
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
			assert(c.isDistinctBy { it.number to it.branch }) {
				"Chapters are not distinct by number: ${c.maxDuplicates { it.number to it.branch }} for $publicUrl"
			}
			assert(c.all { it.source == source })
			checkImageRequest(coverUrl, publicUrl)
			largeCoverUrl?.let {
				checkImageRequest(it, publicUrl)
			}
		}
	}

	@ParameterizedTest(name = "{index}|pages|{0}")
	@MangaSources
	fun pages(source: MangaSource) = runTest {
		val parser = source.newParser(context)
		val list = parser.getList(20, sortOrder = SortOrder.POPULARITY, tags = null)
		val manga = list.first()
		val chapter = parser.getDetails(manga).chapters?.firstOrNull() ?: error("Chapter is null")
		val pages = parser.getPages(chapter)

		assert(pages.isNotEmpty())
		assert(pages.isDistinctBy { it.id })
		assert(pages.all { it.source == source })

		val page = pages.medianOrNull() ?: error("No page")
		val pageUrl = parser.getPageUrl(page)
		assert(pageUrl.isNotEmpty())
		assert(pageUrl.isUrlAbsolute())
		checkImageRequest(pageUrl, page.referer)
	}

	@ParameterizedTest(name = "{index}|favicon|{0}")
	@MangaSources
	fun favicon(source: MangaSource) = runTest {
		val parser = source.newParser(context)
		val faviconUrl = parser.getFaviconUrl()
		assert(faviconUrl.isUrlAbsolute())
		checkImageRequest(faviconUrl, null)
	}

	@ParameterizedTest(name = "{index}|domain|{0}")
	@MangaSources
	fun domain(source: MangaSource) = runTest {
		val parser = source.newParser(context)
		val defaultDomain = parser.getDomain()
		val url = HttpUrl.Builder()
			.host(defaultDomain)
			.scheme("https")
			.toString()
		val response = context.doRequest(url)
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
	fun authorization(source: MangaSource) = runTest {
		val parser = source.newParser(context)
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
		checkImageRequest(testItem.coverUrl, testItem.publicUrl)
	}

	private suspend fun checkImageRequest(url: String, referer: String?) {
		context.doRequest(url, referer).use {
			assert(it.isSuccessful) { "Request failed: ${it.code}(${it.message}): $url" }
			assert(it.mimeType?.startsWith("image/") == true) {
				"Wrong response mime type: ${it.mimeType}"
			}
		}
	}
}