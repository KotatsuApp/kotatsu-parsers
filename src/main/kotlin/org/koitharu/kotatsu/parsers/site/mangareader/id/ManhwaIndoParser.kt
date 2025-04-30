package org.koitharu.kotatsu.parsers.site.mangareader.id

import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("MANHWAINDO", "ManhwaIndo", "id")
internal class ManhwaIndoParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.MANHWAINDO, "manhwaindo.one", pageSize = 30, searchPageSize = 10) {
	override val datePattern = "MMM d, yyyy"
	override val listUrl = "/series"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()
		return coroutineScope {
			docs.select(selectPage).map { img ->
				async { fetchPage(img) }
			}.awaitAll().filterNotNull()
		}
	}

	private suspend fun fetchPage(img: Element): MangaPage? = runCatchingCancellable {
		val url = img.requireSrc().toAbsoluteUrl(domain)
		val response = webClient.httpHead(url)
		if (response.contentType()?.contentType == ContentType.Image.TYPE) {
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		} else {
			null
		}
	}.getOrNull()
}
