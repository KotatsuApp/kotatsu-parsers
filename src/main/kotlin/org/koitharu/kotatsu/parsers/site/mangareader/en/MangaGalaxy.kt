package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.mangareaderParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.Collections.emptyMap

@MangaSourceParser("MANGAGALAXY", "MangaGalaxy", "en")
internal class ResetScans(context: MangaLoaderContext) :
	mangareaderParser(context, MangaParserSource.RESETSCANS, "mangagalaxy.org", 18) {
	override val datePattern = "MM dd"

	override suspend fun loadChapters(mangaUrl: String, document: Document): List<MangaChapter> {
		val doc = if (postReq) {
			val mangaId = document.select("div#manga-chapters-holder").attr("data-id")
			val url = "https://$domain/wp-admin/admin-ajax.php"
			val postData = "action=manga_get_chapters&manga=$mangaId"
			webClient.httpPost(url, postData).parseHtml()
		} else {
			val url = mangaUrl.toAbsoluteUrl(domain).removeSuffix('/') + "/home/ajax/chapters/"
			webClient.httpPost(url, emptyMap()).parseHtml()
		}
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.select(selectChapter).mapChapters(reversed = true) { _, li ->
			val a = li.getElementsByTag("a").findWithText()
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val link = href + stylePage
			val dateText = li.selectFirst("a.c-new-tag")?.attr("title") ?: li.selectFirst(selectDate)?.text()
			val name = a.text()
			MangaChapter(
				id = generateUid(href),
				url = link,
				name = name,
				number = 0f,
				volume = 0,
				branch = null,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				scanlator = null,
				source = source,
			)
		}
	}

	private fun Elements.findWithText() = firstOrNull { it.hasText() } ?: first()
}



'''package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGAGALAXY", "MangaGalaxy", "en")
internal class MangaGalaxy(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.MANGAGALAXY, "mangagalaxy.org", 20, 16) {
	override val listUrl = "/series"
}
'''
