package org.koitharu.kotatsu.parsers.site.liliana.vi

import org.jsoup.Jsoup
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.liliana.LilianaParser
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getBooleanOrDefault

@MangaSourceParser("DOCTRUYEN5S", "DocTruyen5s", "vi")
internal class DocTruyen5s(context: MangaLoaderContext) :
	LilianaParser(context, MangaParserSource.DOCTRUYEN5S, "manga.io.vn", pageSize = 42) {

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val script = doc.selectFirstOrThrow("script:containsData(const CHAPTER_ID)").data()
		val chapterId = script.substringAfter("const CHAPTER_ID = ", "").substringBefore(';', "")
		check(chapterId.isNotEmpty()) { "Không thể tìm thấy CHAPTER_ID, hãy kiểm tra nguồn" }

		val ajaxUrl = buildString {
			append("https://")
			append(domain)
			append("/ajax/image/list/chap/")
			append(chapterId)
		}

		val responseJson = webClient.httpGet(ajaxUrl).parseJson()
		check(responseJson.getBooleanOrDefault("status", false)) { responseJson.getString("msg") }

		val pageListDoc = Jsoup.parse(responseJson.getString("html"))

		return pageListDoc.selectOrThrow("div.separator a").map { element ->
			val url = element.attr("href")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
