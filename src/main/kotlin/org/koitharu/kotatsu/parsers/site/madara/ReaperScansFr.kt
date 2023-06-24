package org.koitharu.kotatsu.parsers.site.madara

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("REAPERSCANS_FR", "ReaperScansFr", "fr")
internal class ReaperScansFr(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.REAPERSCANS_FR, "reaperscans.fr") {

	override val datePattern = "MM/dd/yyyy"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(manga, doc) }
		val root = doc.body().selectFirstOrThrow(".site-content")
		manga.copy(
			tags = root.selectFirst("div.genres-content")?.select("a")?.mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			} ?: manga.tags,
			description = root.requireElementById("nav-profile")
				.selectFirstOrThrow(".description-summary")
				.firstElementChild()?.html(),
			chapters = chaptersDeferred.await(),
		)
	}
}
