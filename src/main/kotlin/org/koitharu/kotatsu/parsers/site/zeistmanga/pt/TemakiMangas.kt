package org.koitharu.kotatsu.parsers.site.zeistmanga.pt

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("TEMAKIMANGAS", "TemakiMangas", "pt")
internal class TemakiMangas(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.TEMAKIMANGAS, "temakimangas.blogspot.com") {

	override suspend fun getFilterOptions() = super.getFilterOptions().copy(
		availableStates = emptySet(),
	)

	override suspend fun fetchAvailableTags(): Set<MangaTag> = emptySet()
	override val selectPage = "#reader div.separator img"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val header = doc.selectFirst("header")

		val state = header?.selectFirst("[data-status]")?.text()?.lowercase().let {
			when (it) {
				"dropado" -> MangaState.ABANDONED
				"finalizada" -> MangaState.FINISHED
				else -> null
			}
		}

		val desc = doc.selectFirst("#synopsis")
		val chaptersDeferred = async { loadChapters(manga.url, doc) }

		manga.copy(
			authors = setOf(doc.select("#extra-info dt:contains(Autor) + dd").text()),
			tags = doc.select("dt:contains(Generos) + dd a").mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast("label/").substringBefore("?"),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc?.text().orEmpty(),
			state = state,
			chapters = chaptersDeferred.await(),
			coverUrl = header?.selectFirst(".thumb")?.attr("src") ?: manga.coverUrl,
			title = header?.selectFirst("h1")?.text() ?: manga.title,
		)
	}

}
