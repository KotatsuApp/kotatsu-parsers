package org.koitharu.kotatsu.parsers.site.madara.fr

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import java.util.Locale

@MangaSourceParser("HENTAISCANTRADVF", "Hentai-Scantrad", "fr")
internal class HentaiScantradVf(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HENTAISCANTRADVF, "hentai.scantrad-vf.cc") {

	override val isNsfwSource = true
	override val datePattern = "d MMMM, yyyy"
	override val sourceLocale: Locale = Locale.FRENCH

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val chaptersDeferred = async { loadChapters(manga.url, doc) }

		val stateselect = doc.body().select("div.summary-content").last()
		val state =
			stateselect?.let {
				when (it.text()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				}
			}

		val desc = doc.body().selectFirst("div.description-summary div.summary__content")?.text()
			?: doc.body() .selectFirst("div.datas_synopsis")?.text()

		manga.copy(
			tags = doc.body().select("div.genres-content a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle =
			doc.body().select(".post-content_item:contains(Alt) .summary-content").firstOrNull()?.tableValue()?.text()
				?.trim() ?: doc.body().select(".post-content_item:contains(Nomes alternativos: ) .summary-content")
				.firstOrNull()?.tableValue()?.text()?.trim(),
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}
}
