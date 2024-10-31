package org.koitharu.kotatsu.parsers.site.wpcomics.ja

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.util.*

// Need to use 0ms.dev Proxy

@MangaSourceParser("MANGARAW", "MangaRaw", "ja")
internal class MangaRaw(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.MANGARAW, "mangaraw.best") {
	override val listUrl = "/search/manga"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val response = when {
			!filter.query.isNullOrEmpty() -> {
				val url = buildString {
					append("https://")
					append(domain)
					append(listUrl)
					append("?keyword=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				val result = runCatchingCancellable { webClient.httpGet(url) }
				val exception = result.exceptionOrNull()
				if (exception is NotFoundException) {
					return emptyList()
				}
				result.getOrThrow()
			}

			else -> {
				val url = buildString {
					append("https://")
					append(domain)
					append(listUrl)
					append("?sort=")
					append(
						when (order) {
							SortOrder.UPDATED -> 0
							SortOrder.POPULARITY -> 10
							SortOrder.NEWEST -> 15
							SortOrder.RATING -> 20
							else -> throw IllegalArgumentException("Sort order ${order.name} not supported")
						},
					)
					if (filter.tags.isNotEmpty()) {
						append("&genre=")
						filter.tags.oneOrThrowIfMany()?.let {
							append(it.key)
						}
					}
					filter.states.oneOrThrowIfMany()?.let {
						append("&status=")
						append(
							when (it) {
								MangaState.ONGOING -> "1"
								MangaState.FINISHED -> "2"
								else -> "-1"
							},
						)
					}
					append("&page=")
					append(page.toString())
				}

				webClient.httpGet(url)
			}
		}
		val tagMap = getOrCreateTagMap()
		return parseMangaList(response.parseHtml(), tagMap)
	}
}
