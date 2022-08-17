package org.koitharu.kotatsu.parsers.site.madara

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("ISEKAISCAN_EU", "IsekaiScan (eu)", "en")
internal class IsekaiScanEuParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ISEKAISCAN_EU, "isekaiscan.eu") {

	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val mangaId = doc.body().requireElementById("manga-chapters-holder").attr("data-id")
		val ul = context.httpPost(
			"https://${getDomain()}/wp-admin/admin-ajax.php",
			mapOf(
				"action" to "manga_get_chapters",
				"manga" to mangaId,
			),
		).parseHtml().body().selectFirstOrThrow("ul")
		val dateFormat = SimpleDateFormat(datePattern, Locale.US)
		return ul.select("li").asReversed().mapChapters { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			MangaChapter(
				id = generateUid(href),
				name = a.ownText(),
				number = i + 1,
				url = href,
				uploadDate = parseChapterDate(
					dateFormat,
					li.selectFirst("span.chapter-release-date i")?.text(),
				),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}
}