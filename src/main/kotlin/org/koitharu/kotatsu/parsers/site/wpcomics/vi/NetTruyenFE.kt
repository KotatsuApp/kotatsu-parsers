package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import androidx.collection.ArrayMap
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.EnumSet

@MangaSourceParser("NETTRUYENFE", "NetTruyenFE", "vi")
internal class NetTruyenFE(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.NETTRUYENFE, "nettruyenfe.com", 20) {

	override val listUrl = "/tim-kiem-nang-cao"

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.RATING,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
		)

	override suspend fun getFilterOptions() = super.getFilterOptions().copy(
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED, MangaState.ABANDONED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val response =
			when {
				!filter.query.isNullOrEmpty() -> {
					val url = buildString {
						append("https://")
						append(domain)
						append("/search")
						append('/')
						append(page.toString())
						append('/')
						append("?keyword=")
						append(filter.query.urlEncoded())
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

						append('/')
						append(page.toString())
						append('/')

						val tagQuery = filter.tags.joinToString(",") { it.key }
						append("?genres=")
						append(tagQuery)

						val tagQueryExclude = filter.tagsExclude.joinToString(",") { it.key }
						append("&notGenres=")
						append(tagQueryExclude)

						append("&sex=All")

						filter.states.oneOrThrowIfMany()?.let {
							append("&status=")
							append(
								when (it) {
									MangaState.ONGOING -> "on-going"
									MangaState.FINISHED -> "completed"
									MangaState.PAUSED -> "on-hold"
									MangaState.ABANDONED -> "canceled"
									else -> "-1"
								},
							)
						}

						append("&chapter_count=0")

						append("&sort=")
						append(
							when (order) {
								SortOrder.UPDATED -> "latest-updated"
								SortOrder.POPULARITY -> "views"
								SortOrder.NEWEST -> "new"
								SortOrder.RATING -> "score"
								SortOrder.ALPHABETICAL -> "az"
								SortOrder.ALPHABETICAL_DESC -> "za"
								else -> null
							},
						)
					}

					webClient.httpGet(url)
				}
			}

		val tagMap = getOrCreateTagMap()
		return parseMangaList(response.parseHtml(), tagMap)
	}

	override suspend fun getOrCreateTagMap(): ArrayMap<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val doc = webClient.httpGet(listUrl.toAbsoluteUrl(domain)).parseHtml()
		val tagItems = doc.select("div.genre-item")
		val result = ArrayMap<String, MangaTag>(tagItems.size)
		for (item in tagItems) {
			val title = item.text()
			val key = item.selectFirstOrThrow("span").attr("data-id")
			if (key.isNotEmpty() && title.isNotEmpty()) {
				result[title] = MangaTag(title = title, key = key, source = source)
			}
		}
		tagCache = result
		result
	}
}
