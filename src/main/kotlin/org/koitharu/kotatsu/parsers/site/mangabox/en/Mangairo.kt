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
import org.koitharu.kotatsu.parsers.model.search.SearchableField.*
import org.koitharu.kotatsu.parsers.site.mangabox.MangaboxParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MANGAIRO", "MangaIro", "en")
internal class Mangairo(context: MangaLoaderContext) :
	MangaboxParser(context, MangaParserSource.MANGAIRO) {
	override val configKeyDomain = ConfigKey.Domain("w.mangairo.com", "chap.mangairo.com")
	override val otherDomain = "chap.mangairo.com"
	override val datePattern = "MMM-dd-yy"
	override val listUrl = "/manga-list"
	override val searchUrl = "/list/search/"
	override val selectDesc = "div#story_discription p"
	override val selectState = "ul.story_info_right li:contains(Status) a"
	override val selectAlt = "ul.story_info_right li:contains(Alter) h2"
	override val selectAut = "ul.story_info_right li:contains(Author) a"
	override val selectTag = "ul.story_info_right li:contains(Genres) a"
	override val selectChapter = "div.chapter_list li"
	override val selectDate = "p"
	override val selectPage = "div.panel-read-story img"
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

	override val searchQueryCapabilities: MangaSearchQueryCapabilities
		get() = MangaSearchQueryCapabilities(
			SearchCapability(
				field = TAG,
				criteriaTypes = setOf(Include::class),
				isMultiValue = false,
			),
			SearchCapability(
				field = TITLE_NAME,
				criteriaTypes = setOf(Match::class),
				isMultiValue = false,
				isExclusive = true,
			),
			SearchCapability(
				field = STATE,
				criteriaTypes = setOf(Include::class),
				isMultiValue = false,
			),
		)

	private fun Any?.toQueryParam(): String = when (this) {
		is String -> replace(" ", "_").urlEncoded()
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
			else -> "latest"
		}

		else -> this.toString().urlEncoded()
	}

	override suspend fun getListPage(query: MangaSearchQuery, page: Int): List<Manga> {
		var titleSearchUrl: String? = null
		var category = "all"
		var state = "all"

		val url = buildString {
			append("https://${domain}${listUrl}")
			append("/type-${(query.order ?: defaultSortOrder).toQueryParam()}")

			query.criteria.forEach { criterion ->
				when (criterion) {
					is Include<*> -> {
						when (criterion.field) {
							TAG -> category = criterion.values.first().toQueryParam()
							STATE -> state = criterion.values.first().toQueryParam()
							else -> Unit
						}
					}

					is Match<*> -> {
						if (criterion.field == TITLE_NAME) {
							criterion.value.toQueryParam().takeIf { it.isNotBlank() }?.let { titleName ->
								titleSearchUrl = "https://${domain}${searchUrl}${titleName}/" +
									"?page=${query.offset}"
							}
						}
					}

					else -> {
						// Not supported
					}
				}
			}
			append("/ctg-$category")
			append("/state-$state")
			append("/page-$page")
		}

		val doc = webClient.httpGet(titleSearchUrl ?: url).parseHtml()

		return doc.select("div.story-item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = (div.selectFirst("h2")?.text() ?: div.selectFirst("h3")?.text()).orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				authors = emptySet(),
				state = null,
				source = source,
				contentRating = if (isNsfwSource) ContentRating.ADULT else ContentRating.SAFE,
			)
		}
	}

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
					append("/type-")
					when (order) {
						SortOrder.POPULARITY -> append("topview")
						SortOrder.UPDATED -> append("latest")
						SortOrder.NEWEST -> append("newest")
						else -> append("latest")
					}

					append("/ctg-")
					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append(it.key)
						}
					} else {
						append("all")
					}

					append("/state-")
					if (filter.states.isNotEmpty()) {
						filter.states.oneOrThrowIfMany()?.let {
							append(
								when (it) {
									MangaState.ONGOING -> "ongoing"
									MangaState.FINISHED -> "completed"
									else -> "all"
								},
							)
						}
					} else {
						append("all")
					}

					append("/page-")
				}
			}
			append(page.toString())
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.story-item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = (div.selectFirst("h2")?.text() ?: div.selectFirst("h3")?.text()).orEmpty(),
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

	override suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl/type-latest/ctg-all/state-all/page-1").parseHtml()
		return doc.select("div.panel_category a:not(.ctg_select)").mapToSet { a ->
			val key = a.attr("href").substringAfterLast("ctg-").substringBefore("/")
			val name = a.attr("title").replace("Category ", "")
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(doc) }
		val desc = doc.selectFirst(selectDesc)?.html()?.nullIfEmpty()
		val stateDiv = doc.select(selectState).text()
		val state = stateDiv.let {
			when (it) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}

		val alt = doc.body().select(selectAlt).text().replace("Alternative : ", "").nullIfEmpty()
		val aut = doc.body().select(selectAut).eachText().joinToString().nullIfEmpty()
		manga.copy(
			tags = doc.body().select(selectTag).mapToSet { a ->
				MangaTag(
					key = a.attr("href")
						.substringAfterLast("page-"), // Yes the site, it's crashing between page is tag id
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
			author = aut,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}
}
