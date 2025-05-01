package org.koitharu.kotatsu.parsers.site.hotcomics.en

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.hotcomics.HotComicsParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("HOTCOMICS", "HotComics", "en")
internal class HotComics(context: MangaLoaderContext) :
	HotComicsParser(context, MangaParserSource.HOTCOMICS, "hotcomics.me/en") {
	
	override suspend fun getDetails(manga: Manga): Manga {
		val mangaUrl = manga.url.toAbsoluteUrl(domain)
		val redirectHeaders = Headers.Builder().set("Referer", mangaUrl).build()
		val doc = webClient.httpGet(mangaUrl, redirectHeaders).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return manga.copy(
			description = doc.selectFirst("div.title_content_box h2")?.text() ?: manga.description,
			chapters = doc.select(selectMangaChapters).mapChapters { i, li ->
				val a = li.selectFirstOrThrow("a")
				val href = a.attr("href")
				val url = if (href.startsWith("/")) {
					"/" + href.removePrefix("/").substringAfter('/') // remove /$lang/url
				} else if (href.startsWith("javascript")) {
					val h = a.attr("onclick").substringAfterLast("popupLogin('").substringBefore("'")
					"/" + h.removePrefix("/").substringAfter('/') // remove /$lang/url
				} else {
					href
				}
				val chapterNum = li.selectFirst(".num")?.text()?.toFloat() ?: (i + 1f)
				MangaChapter(
					id = generateUid(url),
					title = null,
					number = chapterNum,
					volume = 0,
					url = url,
					scanlator = null,
					uploadDate = dateFormat.tryParse(li.selectFirst("time")?.attr("datetime")),
					branch = null,
					source = source,
				)
			},
		)
	}
}
