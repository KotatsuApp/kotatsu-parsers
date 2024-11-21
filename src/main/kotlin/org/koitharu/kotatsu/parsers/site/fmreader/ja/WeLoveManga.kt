package org.koitharu.kotatsu.parsers.site.fmreader.ja

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.fmreader.FmreaderParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat

@MangaSourceParser("WELOVEMANGA", "WeLoveManga", "ja")
internal class WeLoveManga(context: MangaLoaderContext) :
	FmreaderParser(context, MangaParserSource.WELOVEMANGA, "welovemanga.one") {

	override suspend fun getChapters(doc: Document): List<MangaChapter> {
		val mid = doc.selectFirstOrThrow("div.cmt input").attr("value")
		val docLoad =
			webClient.httpGet("https://$domain/app/manga/controllers/cont.Listchapter.php?mid=$mid").parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return docLoad.body().select(selectChapter).mapChapters(reversed = true) { i, a ->
			val href = a.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			val dateText = a.selectFirst(selectDate)?.text()
			MangaChapter(
				id = generateUid(href),
				name = a.selectFirstOrThrow("a").text(),
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val cid = doc.selectFirstOrThrow("#chapter").attr("value")
		val docLoad = webClient.httpGet("https://$domain/app/manga/controllers/cont.listImg.php?cid=$cid").parseHtml()
		return docLoad.select("img").map { img ->
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
