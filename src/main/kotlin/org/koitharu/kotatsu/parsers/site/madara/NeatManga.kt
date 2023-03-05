package org.koitharu.kotatsu.parsers.site.madara

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

@MangaSourceParser("NEATMANGA", "NeatManga", "en")
internal class NeatManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.NEATMANGA, "neatmangas.com") {

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val chaptersDeferred = async { getChapters(manga) }
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirst("div.profile-manga")
			?.selectFirst("div.summary_content")
			?.selectFirst("div.post-content")
			?: throw ParseException("Root not found", fullUrl)
		val root2 = doc.body().selectFirst("div.content-area")
			?.selectFirst("div.c-page")
			?: throw ParseException("Root2 not found", fullUrl)
		manga.copy(
			tags = root.selectFirst("div.genres-content")?.select("a")
				?.mapNotNullToSet { a ->
					MangaTag(
						key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
						title = a.text().toTitleCase(),
						source = source,
					)
				} ?: manga.tags,
			description = root2.getElementsMatchingOwnText("Summary")
				.firstOrNull()
				?.nextElementSibling()
				?.select("p")
				?.filterNot { it.ownText().startsWith("A brief description") }
				?.joinToString { it.html() },
			chapters = chaptersDeferred.await(),
		)
	}

	private suspend fun getChapters(manga: Manga): List<MangaChapter> {
		val slug = manga.url.removeSuffix('/').substringAfterLast('/')
		val doc2 = webClient.httpPost(
			"https://$domain/manga/$slug/ajax/chapters/",
			mapOf(),
		).parseHtml()
		val ul = doc2.body().selectFirstOrThrow("ul")
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
