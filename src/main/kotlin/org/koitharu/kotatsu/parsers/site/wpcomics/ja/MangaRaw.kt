package org.koitharu.kotatsu.parsers.site.wpcomics.ja

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.parsers.util.urlEncoded

// Need to use 0ms.dev Proxy

@MangaSourceParser("MANGARAW", "MangaRaw", "ja")
internal class MangaRaw(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.MANGARAW, "mangaraw.xyz") {
	override val listUrl = "/search/manga"

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val response =
			when (filter) {
				is MangaListFilter.Search -> {
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

				is MangaListFilter.Advanced -> {
					val url = buildString {
						append("https://")
						append(domain)
						append(listUrl)
						append("?sort=")
						append(
							when (filter.sortOrder) {
								SortOrder.UPDATED -> 0
								SortOrder.POPULARITY -> 10
								SortOrder.NEWEST -> 15
								SortOrder.RATING -> 20
								else -> throw IllegalArgumentException("Sort order ${filter.sortOrder.name} not supported")
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

				null -> {
					val url = buildString {
						append("https://")
						append(domain)
						append(listUrl)
						append("?genres=&notgenres=&gender=-1&status=-1&minchapter=1&sort=0&page=")
						append(page.toString())
					}
					webClient.httpGet(url)
				}
			}
		val tagMap = getOrCreateTagMap()
		return parseMangaList(response.parseHtml(), tagMap)
	}
}
