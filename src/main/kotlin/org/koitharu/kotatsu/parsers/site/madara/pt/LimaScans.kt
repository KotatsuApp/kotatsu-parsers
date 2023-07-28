package org.koitharu.kotatsu.parsers.site.madara.pt

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat

@MangaSourceParser("LIMASCANS", "Lima Scans", "pt", ContentType.HENTAI)
internal class LimaScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LIMASCANS, "limascans.xyz/v2", 10) {

	override val postreq = true
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"

	override suspend fun loadChapters(mangaUrl: String, document: Document): List<MangaChapter> {


		val mangaId = document.select("div#manga-chapters-holder").attr("data-id")
		val url = "https://$domain/wp-admin/admin-ajax.php"
		val postdata = "action=manga_get_chapters&manga=$mangaId"
		val doc = webClient.httpPost(url, postdata).parseHtml()

		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)

		return doc.select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val link = href + stylepage
			val dateText = li.selectFirst("a.c-new-tag")?.attr("title") ?: li.selectFirst(selectDate)?.text()
			val name = a.selectFirst("p")?.text() ?: a.ownText()
			MangaChapter(
				id = generateUid(href),
				url = link.replace("/v2", ""),
				name = name,
				number = i + 1,
				branch = null,
				uploadDate = parseChapterDate(dateFormat, dateText),
				scanlator = null,
				source = source,
			)
		}
	}
}
