package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseHtml
import java.lang.IllegalArgumentException
import java.util.EnumSet

@MangaSourceParser("RIZZCOMIC", "RizzComic", "en")
internal class RizzComic(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.RIZZCOMIC, "rizzcomic.com", pageSize = 50, searchPageSize = 20) {
	override val datePattern = "dd MMM yyyy"
	override val listUrl = "/series"

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)
	override val availableStates: Set<MangaState> = emptySet()
	override val isMultipleTagsSupported = false

	// TODO Query created in json
	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		if (page > 1) {
			return emptyList()
		}
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {

				is MangaListFilter.Search -> {
					throw IllegalArgumentException("Search is not supported by this source")
				}

				is MangaListFilter.Advanced -> {

					if (filter.tags.isNotEmpty()) {
						append("/genre/")
						filter.tags.oneOrThrowIfMany()?.let {
							append(it.key)
						}
					} else {
						append(listUrl)
					}
				}

				null -> {
					append(listUrl)
				}
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}
}
