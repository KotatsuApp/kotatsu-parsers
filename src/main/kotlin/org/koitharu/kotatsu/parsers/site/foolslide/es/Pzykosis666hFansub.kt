package org.koitharu.kotatsu.parsers.site.foolslide.es

import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.foolslide.FoolSlideParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("PZYKOSIS666HFANSUB", "Pzykosis666h Fansub", "es", ContentType.HENTAI)
internal class Pzykosis666hFansub(context: MangaLoaderContext) :
	FoolSlideParser(context, MangaParserSource.PZYKOSIS666HFANSUB, "lector.pzykosis666hfansub.com") {

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val testAdultPage = webClient.httpGet(fullUrl).parseHtml()
		val doc = if (testAdultPage.selectFirst("div.info form") != null) {
			webClient.httpPost(fullUrl, "adult=true").parseHtml()
		} else {
			testAdultPage
		}
		val chapters = getChapters(doc)
		val desc = if (doc.selectFirstOrThrow(selectInfo).html().contains("Descripción")) {
			doc.selectFirstOrThrow(selectInfo).text().substringAfter("Descripción: ").substringBefore("Lecturas")
		} else {
			doc.selectFirstOrThrow(selectInfo).text()
		}
		val author = if (doc.selectFirstOrThrow(selectInfo).html().contains("Author")) {
			doc.selectFirstOrThrow(selectInfo).text().substringAfter("Author: ").substringBefore("Art")
		} else {
			null
		}
		manga.copy(
			coverUrl = doc.selectFirst(".thumbnail img")?.src() ?: manga.coverUrl,
			description = desc,
			altTitle = null,
			author = author,
			state = null,
			chapters = chapters,
		)
	}
}
