package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("DOUJIN_HENTAI_NET", "DoujinHentai.net", "es", ContentType.HENTAI)
internal class DoujinHentaiNet(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.DOUJIN_HENTAI_NET, "doujinhentai.net", 18) {

	override val datePattern = "dd MMM. yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
	override val listUrl = "lista-manga-hentai/"
	override val tagPrefix = "lista-manga-hentai/category/"
	override val selectTestAsync = "div.listing-chapters_wrap"
	override val selectChapter = "li.wp-manga-chapter:contains(Capitulo)"
	override val selectPage = "div#all img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPage).map { div ->
			val img = div.selectFirstOrThrow("img")
			val url = img.requireSrc().toRelativeUrl(domain)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
