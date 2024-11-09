package org.koitharu.kotatsu.parsers.site.mangabox.en

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.mangabox.MangaboxParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MANGAKAKALOTTV", "Mangakakalot.tv", "en")
internal class MangakakalotTv(context: MangaLoaderContext) :
	MangaboxParser(context, MangaParserSource.MANGAKAKALOTTV) {

	override val configKeyDomain = ConfigKey.Domain("ww8.mangakakalot.tv")
	override val searchUrl = "/search/"
	override val listUrl = "/manga_list"
	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
	)
	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
			isMultipleTagsSupported = false,
			isSearchWithFiltersSupported = false,
		)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {

				!filter.query.isNullOrEmpty() -> {
					append(searchUrl)
					append(filter.query.urlEncoded())
					append("?page=")
				}

				else -> {
					append(listUrl)
					append("?type=")
					when (order) {
						SortOrder.POPULARITY -> append("topview")
						SortOrder.UPDATED -> append("latest")
						SortOrder.NEWEST -> append("newest")
						else -> append("latest")
					}
					if (filter.tags.isNotEmpty()) {
						append("&category=")
						filter.tags.oneOrThrowIfMany()?.let {
							append(it.key)
						}
					}

					filter.states.oneOrThrowIfMany()?.let {
						append("&state=")
						append(
							when (it) {
								MangaState.ONGOING -> "Ongoing"
								MangaState.FINISHED -> "Completed"
								else -> "all"
							},
						)
					}

					append("&page=")
				}
			}
			append(page.toString())
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.list-truyen-item-wrap").ifEmpty {
			doc.select("div.story_item")
		}.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("h3").text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(doc) }
		val desc = doc.selectFirstOrThrow(selectDesc).html()
		val stateDiv = doc.select(selectState).text().replace("Status : ", "")
		val state = stateDiv.let {
			when (it.lowercase()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}
		val alt = doc.body().select(selectAlt).text().replace("Alternative : ", "")
		val aut = doc.body().select(selectAut).eachText().joinToString()
		manga.copy(
			tags = doc.body().select(selectTag).mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast("category=").substringBefore("&"),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
			author = aut,
			state = state,
			chapters = chaptersDeferred.await(),
			isNsfw = manga.isNsfw,
		)
	}

	override val selectTagMap = "ul.tag li a"

	override suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		return doc.select(selectTagMap).mapToSet { a ->
			MangaTag(
				key = a.attr("href").substringAfterLast("category=").substringBefore("&"),
				title = a.attr("title"),
				source = source,
			)
		}
	}
}
