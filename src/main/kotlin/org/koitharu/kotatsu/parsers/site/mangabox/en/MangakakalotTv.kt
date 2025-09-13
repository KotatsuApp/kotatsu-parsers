package org.koitharu.kotatsu.parsers.site.mangabox.en

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria.Include
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria.Match
import org.koitharu.kotatsu.parsers.model.search.SearchCapability
import org.koitharu.kotatsu.parsers.model.search.SearchableField
import org.koitharu.kotatsu.parsers.model.search.SearchableField.*
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

	override val searchQueryCapabilities: MangaSearchQueryCapabilities
		get() = MangaSearchQueryCapabilities(
			SearchCapability(
				field = TAG,
				criteriaTypes = setOf(Include::class),
				isMultiple = false,
			),
			SearchCapability(
				field = TITLE_NAME,
				criteriaTypes = setOf(Match::class),
				isMultiple = false,
				isExclusive = true,
			),
			SearchCapability(
				field = STATE,
				criteriaTypes = setOf(Include::class),
				isMultiple = false,
			),
		)

	private fun SearchableField.toParamName(): String = when (this) {
		TAG -> "category"
		STATE -> "state"
		else -> ""
	}

	private fun Any?.toQueryParam(): String = when (this) {
		is String -> urlEncoded()
		is MangaTag -> key
		is MangaState -> when (this) {
			MangaState.ONGOING -> "ongoing"
			MangaState.FINISHED -> "completed"
			else -> "all"
		}

		is SortOrder -> when (this) {
			SortOrder.POPULARITY -> "topview"
			SortOrder.UPDATED -> "latest"
			SortOrder.NEWEST -> "newest"
			else -> ""
		}

		else -> this.toString().urlEncoded()
	}

	private fun StringBuilder.appendCriterion(field: SearchableField, value: Any?, paramName: String? = null) {
		val param = paramName ?: field.toParamName()
		if (param.isNotBlank()) {
			append("&$param=")
			append(value.toQueryParam())
		}
	}

	override suspend fun getListPage(query: MangaSearchQuery, page: Int): List<Manga> {
		var titleSearchUrl: String? = null
		val url = buildString {
			val pageQueryParameter = "page=$page"
			append("https://$domain/?")

			query.criteria.forEach { criterion ->
				when (criterion) {
					is Include<*> -> {
						criterion.field.toParamName().takeIf { it.isNotBlank() }?.let { param ->
							append("&$param=${criterion.values.first().toQueryParam()}")
						}
					}

					is Match<*> -> {
						if (criterion.field == TITLE_NAME) {
							criterion.value.toQueryParam().takeIf { it.isNotBlank() }?.let { titleName ->
								titleSearchUrl = "https://${domain}${searchUrl}${titleName}/" +
									"?$pageQueryParameter"
							}
						}
						appendCriterion(criterion.field, criterion.value)
					}

					else -> {
						// Not supported
					}
				}
			}

			append("&$pageQueryParameter")
			append("&type=${(query.order ?: defaultSortOrder).toQueryParam()}")
		}

		val doc = webClient.httpGet(titleSearchUrl ?: url).parseHtml()

		return doc.select("div.list-truyen-item-wrap").ifEmpty {
			doc.select("div.story_item")
		}.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src(),
				title = div.selectFirstOrThrow("h3").text(),
				altTitles = emptySet(),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				authors = emptySet(),
				state = null,
				source = source,
				contentRating = null,
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
		val alt = doc.body().select(selectAlt).text().replace("Alternative : ", "").nullIfEmpty()
		val author = doc.body().select(selectAut).eachText().joinToString().nullIfEmpty()
		manga.copy(
			tags = doc.body().select(selectTag).mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast("category=").substringBefore("&"),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitles = setOfNotNull(alt),
			authors = setOfNotNull(author),
			state = state,
			chapters = chaptersDeferred.await(),
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
