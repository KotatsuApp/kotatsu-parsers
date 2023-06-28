package org.koitharu.kotatsu.parsers.site.madara

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("HENTAI_4FREE", "Hentai4Free", "en")
internal class Hentai4Free(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HENTAI_4FREE, "hentai4free.net", pageSize = 24) {

	override val tagPrefix = "hentai-tag/"

	override val isNsfwSource = true

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml()
		val body = doc.body()
		val root1 = body.selectFirst("header")?.selectFirst("ul.second-menu")
		val list = root1?.select("li").orEmpty()
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
				title = a.ownText().trim().toTitleCase(),
				source = source,
			)
		}
	}

	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val slug = manga.url.removeSuffix('/').substringAfterLast('/')
		val doc2 = webClient.httpPost(
			"https://$domain/hentai/$slug/ajax/chapters/",
			mapOf(),
		).parseHtml()
		val ul = doc2.body().selectFirstOrThrow("ul")
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
