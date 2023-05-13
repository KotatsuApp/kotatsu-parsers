package org.koitharu.kotatsu.parsers.site.madara

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("ISEKAISCAN_EU", "IsekaiScan", "en")
internal class IsekaiScanEuParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ISEKAISCAN_EU, "isekaiscan.to") {

	override val datePattern = "MM/dd/yyyy"

	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		doc.selectFirst("ul.version-chap")?.let {
			return parseChapters(it)
		}
		val mangaId = doc.body().requireElementById("manga-chapters-holder").attr("data-id")
		val ul = webClient.httpPost(
			"https://${domain}/wp-admin/admin-ajax.php",
			mapOf(
				"action" to "manga_get_chapters",
				"manga" to mangaId,
			),
		).parseHtml().body().selectFirstOrThrow("ul")
		return parseChapters(ul)
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/mangax/").parseHtml()
		val body = doc.body()
		val root1 = body.selectFirst("header")?.selectFirst("ul.second-menu")
		val root2 = body.selectFirst("div.genres_wrap")?.selectFirst("ul.list-unstyled")
		if (root1 == null && root2 == null) {
			doc.parseFailed("Root not found")
		}
		val list = root1?.select("li").orEmpty() + root2?.select("li").orEmpty()
		val keySet = HashSet<String>(list.size)
		return list.mapNotNullToSet { li ->
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			val href = a.attr("href").removeSuffix("/")
				.substringAfterLast(tagPrefix, "")
			if (href.isEmpty() || !keySet.add(href)) {
				return@mapNotNullToSet null
			}
			MangaTag(
				key = href,
				title = a.ownText().toTitleCase(Locale.ENGLISH),
				source = source,
			)
		}
	}

	private fun parseChapters(ul: Element): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, Locale.US)
		return ul.select("li").mapChapters(reversed = true) { i, li ->
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
