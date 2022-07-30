package org.koitharu.kotatsu.parsers.site.madara

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TATAKAE_SCANS", "Tatakae Scans", "pt-BR")
internal class TatakaeScansParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TATAKAE_SCANS, "tatakaescan.com") {

	override suspend fun getDetails(manga: Manga): Manga {
		val fullUrl = manga.url.toAbsoluteUrl(getDomain())
		val doc = context.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirst("div.profile-manga")
			?.selectFirst("div.summary_content")
			?.selectFirst("div.post-content")
			?: throw ParseException("Root not found", fullUrl)
		val root2 = doc.body().selectFirst("div.content-area")
			?.selectFirst("div.c-page")
			?: throw ParseException("Root2 not found", fullUrl)
		val dateFormat = SimpleDateFormat("dd 'de' MMMMM 'de' yyyy", Locale("pt", "BR"))
		return manga.copy(
			tags = root.selectFirst("div.genres-content")?.select("a")
				?.mapNotNullToSet { a ->
					MangaTag(
						key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
						title = a.text().toTitleCase(),
						source = source,
					)
				} ?: manga.tags,
			description = root.selectFirst("div.post-content")
				?.select("p")
				?.joinToString { it.html() },
			chapters = root2.select("li").asReversed().mapChapters { i, li ->
					val a = li.selectFirst("a")
					val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
					MangaChapter(
						id = generateUid(href),
						name = a.ownText(),
						number = i + 1,
						url = href,
						uploadDate = parseChapterDate(
							dateFormat,
							li.selectFirst("span.chapter-release-date")?.text(),
						),
						source = source,
						scanlator = null,
						branch = null,
					)
				},
		)
	}

}