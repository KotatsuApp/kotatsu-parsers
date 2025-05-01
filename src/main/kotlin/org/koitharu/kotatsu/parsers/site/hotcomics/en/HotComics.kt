package org.koitharu.kotatsu.parsers.site.hotcomics.en

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.site.hotcomics.HotComicsParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat

@MangaSourceParser("HOTCOMICS", "HotComics", "en")
internal class HotComics(context: MangaLoaderContext) :
	HotComicsParser(context, MangaParserSource.HOTCOMICS, "hotcomics.me/en") {
	
	override suspend fun getDetails(manga: Manga): Manga {
		val mangaUrl = manga.url.toAbsoluteUrl(domain)
		val redirectHeaders = Headers.Builder().set("Referer", mangaUrl).build()
		val doc = webClient.httpGet(mangaUrl, redirectHeaders).parseHtml()
		val chapters = doc.select("#tab-chapter a").mapChapters { i, element ->
			val url = element.attr("onclick").substringAfter("popupLogin('").substringBefore("'")
			val name = element.selectFirst(".cell-num")?.text() ?: "Unknown"
			val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
			val dateUpload = dateFormat.tryParse(element.selectFirst(".cell-time")?.text())
			val chapterNum = element.selectFirst(".num")?.text()?.toFloat() ?: (i + 1f)
			MangaChapter(
				id = generateUid(url),
				title = name,
				number = chapterNum,
				volume = 0,
				url = url,
				scanlator = null,
				uploadDate = dateUpload,
				branch = null,
				source = source,
			)
		}

		return manga.copy(
			description = doc.selectFirst("div.title_content_box h2")?.text() ?: manga.description,
			chapters = chapters,
		)
	}
}