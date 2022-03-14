package org.koitharu.kotatsu.parsers

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.medianOrNull
import org.koitharu.kotatsu.parsers.util.mimeType
import org.koitharu.kotatsu.test_util.isDistinct
import org.koitharu.kotatsu.test_util.isDistinctBy
import org.koitharu.kotatsu.test_util.isUrlAbsoulte

internal class MangaParserTest {

	private val context = MangaLoaderContextMock()

	@ParameterizedTest
	@MangaSources
	fun list(source: MangaSource) = runTest {
		val parser = source.newParser(context)
		val list = parser.getList(20, query = null, sortOrder = SortOrder.POPULARITY, tags = null)
		checkMangaList(list)
		assert(list.all { it.source == source })
	}

	@ParameterizedTest
	@MangaSources
	fun search(source: MangaSource) = runTest {
		val parser = source.newParser(context)
		val subject = parser.getList(20, query = null, sortOrder = SortOrder.POPULARITY, tags = null).minByOrNull {
			it.title.length
		} ?: error("No manga found")
		val list = parser.getList(offset = 0, query = subject.title, sortOrder = null, tags = null)
		assert(list.singleOrNull { it.url == subject.url && it.id == subject.id } != null) {
			"Single subject ${subject.title} not found in search results"
		}
		checkMangaList(list)
		assert(list.all { it.source == source })
	}

	@ParameterizedTest
	@MangaSources
	fun tags(source: MangaSource) = runTest {
		val parser = source.newParser(context)
		val tags = parser.getTags()
		assert(tags.isNotEmpty())
		val keys = tags.map { it.key }
		assert(keys.isDistinct())
		assert("" !in keys)
		val titles = tags.map { it.title }
		assert(titles.isDistinct())
		assert("" !in titles)
		assert(tags.all { it.source == source })

		val list = parser.getList(offset = 0, tags = setOf(tags.last()), query = null, sortOrder = null)
		checkMangaList(list)
		assert(list.all { it.source == source })
	}

	@ParameterizedTest
	@MangaSources
	fun details(source: MangaSource) = runTest {
		val parser = source.newParser(context)
		val list = parser.getList(20, query = null, sortOrder = SortOrder.POPULARITY, tags = null)
		val manga = list[3]
		parser.getDetails(manga).apply {
			assert(!chapters.isNullOrEmpty())
			assert(publicUrl.isUrlAbsoulte())
			assert(description != null)
			assert(title.startsWith(manga.title))
			assert(this.source == source)
			val c = checkNotNull(chapters)
			assert(c.isDistinctBy { it.id })
			assert(c.isDistinctBy { it.number })
			assert(c.isDistinctBy { it.name })
			assert(c.all { it.source == source })
		}
	}

	@ParameterizedTest
	@MangaSources
	fun pages(source: MangaSource) = runTest {
		val parser = source.newParser(context)
		val list = parser.getList(20, query = null, sortOrder = SortOrder.POPULARITY, tags = null)
		val manga = list.first()
		val chapter = parser.getDetails(manga).chapters?.firstOrNull() ?: error("Chapter is null")
		val pages = parser.getPages(chapter)

		assert(pages.isNotEmpty())
		assert(pages.isDistinctBy { it.id })
		assert(pages.all { it.source == source })

		val page = pages.medianOrNull() ?: error("No page")
		val pageUrl = parser.getPageUrl(page)
		assert(pageUrl.isNotEmpty())
		assert(pageUrl.isUrlAbsoulte())
		val pageResponse = context.doRequest(pageUrl) {
			header("Referrer", page.referer)
		}
		assert(pageResponse.isSuccessful)
		assert(pageResponse.mimeType?.startsWith("image/") == true)
	}

	private fun checkMangaList(list: List<Manga>) {
		assert(list.isNotEmpty()) { "Manga list is empty" }
		assert(list.isDistinctBy { it.id }) { "Manga list contains duplicated ids" }
		for (item in list) {
			assert(item.url.isNotEmpty())
			assert(!item.url.isUrlAbsoulte())
			assert(item.coverUrl.isUrlAbsoulte())
			assert(item.title.isNotEmpty())
			assert(item.publicUrl.isUrlAbsoulte())
		}
	}
}