package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("MANHWAINDO", "ManhwaIndo", "id")
internal class ManhwaIndoParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.MANHWAINDO, "manhwaindo.one", pageSize = 30, searchPageSize = 10) {
	override val datePattern = "MMM d, yyyy"
	override val listUrl = "/series"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()
		return docs.select(selectPage).mapNotNull { img ->
			val url = img.attr("data-src").takeIf { it.isNotBlank() }?.toRelativeUrl(domain) ?: return@mapNotNull null
			try {
				val response = webClient.httpHead(url)
				if (response.headers["Content-Type"]?.startsWith("image/") == true) {
					MangaPage(
						id = generateUid(url),
						url = url,
						preview = null,
						source = source,
					)
				} else {
					null
				}
			} catch (e: Exception) {
				null
			}
		}
	}
}
