package org.koitharu.kotatsu.parsers.site.keyoapp.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.keyoapp.KeyoappParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.parseFailed
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl

@MangaSourceParser("MAGUSMANGA", "MagusToon", "en")
internal class MagusToon(context: MangaLoaderContext) :
	KeyoappParser(context, MangaParserSource.MAGUSMANGA, "magustoon.com") {

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPage).map { img ->
			val id = img.attr("uid") ?: img.parseFailed("Image id not found")
			val url = "https://cdn.$domain/x/$id"
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
