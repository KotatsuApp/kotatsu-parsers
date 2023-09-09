package org.koitharu.kotatsu.parsers.site.foolslide.en


import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.foolslide.FoolSlideParser
import org.koitharu.kotatsu.parsers.util.*


@MangaSourceParser("SEINAGI", "Seinagi", "en")
internal class Seinagi(context: MangaLoaderContext) :
	FoolSlideParser(context, MangaSource.SEINAGI, "reader.seinagi.org.es") {

	override val pagination = false

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val testAdultPage = webClient.httpGet(fullUrl).parseHtml()

		val doc = if (testAdultPage.selectFirst("div.info form") != null) {
			webClient.httpPost(fullUrl, "adult=true").parseHtml()
		} else {
			testAdultPage
		}
		val chapters = getChapters(manga, doc)

		val desc = if (doc.selectFirstOrThrow(selectInfo).html().contains("Description")) {
			doc.selectFirstOrThrow(selectInfo).text().substringAfter("Description: ").substringBefore("Readings")
		} else {
			doc.selectFirstOrThrow(selectInfo).text()
		}

		val author = if (doc.selectFirstOrThrow(selectInfo).html().contains("Author")) {
			doc.selectFirstOrThrow(selectInfo).text().substringAfter("Author: ").substringBefore("Art")
		} else {
			null
		}

		manga.copy(
			tags = emptySet(),
			coverUrl = doc.selectFirst(".thumbnail img")?.src().orEmpty(),// for manga result on search
			description = desc,
			altTitle = null,
			author = author,
			state = null,
			chapters = chapters,
		)
	}
}
