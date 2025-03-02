package org.koitharu.kotatsu.parsers.site.mangabox

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria.*
import org.koitharu.kotatsu.parsers.model.search.SearchCapability
import org.koitharu.kotatsu.parsers.model.search.SearchableField
import org.koitharu.kotatsu.parsers.model.search.SearchableField.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

internal abstract class MangaboxParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	pageSize: Int = 24,
) : PagedMangaParser(context, source, pageSize) {

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
	)

	override val searchQueryCapabilities: MangaSearchQueryCapabilities
		get() = MangaSearchQueryCapabilities(
			SearchCapability(
				field = TAG,
				criteriaTypes = setOf(Include::class, Exclude::class),
				isMultiple = true,
			),
			SearchCapability(
				field = TITLE_NAME,
				criteriaTypes = setOf(Match::class),
				isMultiple = false,
			),
			SearchCapability(
				field = STATE,
				criteriaTypes = setOf(Include::class),
				isMultiple = true,
			),
			SearchCapability(
				field = AUTHOR,
				criteriaTypes = setOf(Include::class),
				isMultiple = false,
				isExclusive = true,
			),
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	@JvmField
	protected val ongoing: Set<String> = setOf(
		"ongoing",
	)

	@JvmField
	protected val finished: Set<String> = setOf(
		"completed",
	)

	protected open val listUrl = "/advanced_search"
	protected open val authorUrl = "/search/author"
	protected open val searchUrl = "/search/story/"
	protected open val datePattern = "MMM dd,yy"

	private fun SearchableField.toParamName(): String = when (this) {
		TITLE_NAME, AUTHOR -> "keyw"
		TAG -> "g_i"
		STATE -> "sts"
		else -> ""
	}

	private fun Any?.toQueryParam(): String = when (this) {
		is String -> replace(" ", "_").urlEncoded()
		is MangaTag -> key
		is MangaState -> when (this) {
			MangaState.ONGOING -> "ongoing"
			MangaState.FINISHED -> "completed"
			else -> ""
		}

		is SortOrder -> when (this) {
			SortOrder.ALPHABETICAL -> "az"
			SortOrder.NEWEST -> "newest"
			SortOrder.POPULARITY -> "topview"
			else -> ""
		}

		else -> this.toString().replace(" ", "_").urlEncoded()
	}

	private fun StringBuilder.appendCriterion(field: SearchableField, value: Any?, paramName: String? = null) {
		val param = paramName ?: field.toParamName()
		if (param.isNotBlank()) {
			append("&$param=")
			append(value.toQueryParam())
		}
	}

	override suspend fun getListPage(query: MangaSearchQuery, page: Int): List<Manga> {
		var authorSearchUrl: String? = null
		val url = buildString {
			val pageQueryParameter = "page=$page"
			append("https://${domain}${listUrl}/?s=all")

			query.criteria.forEach { criterion ->
				when (criterion) {
					is Include<*> -> {
						if (criterion.field == AUTHOR) {
							criterion.values.firstOrNull()?.toQueryParam()?.takeIf { it.isNotBlank() }
								?.let { authorKey ->
									authorSearchUrl = "https://${domain}${authorUrl}/${authorKey}/?$pageQueryParameter"
								}
						}

						criterion.field.toParamName().takeIf { it.isNotBlank() }?.let { param ->
							append("&$param=${criterion.values.joinToString("_") { it.toQueryParam() }}")
						}
					}

					is Exclude<*> -> {
						append("&g_e=${criterion.values.joinToString("_") { it.toQueryParam() }}")
					}

					is Match<*> -> {
						appendCriterion(criterion.field, criterion.value)
					}

					else -> {
						// Not supported
					}
				}
			}

			append("&${pageQueryParameter}")
			append("&orby=${(query.order ?: defaultSortOrder).toQueryParam()}")
		}

		val doc = webClient.httpGet(authorSearchUrl ?: url).parseHtml()

		return doc.select("div.content-genres-item, div.list-story-item").ifEmpty {
			doc.select("div.search-story-item")
		}.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src(),
				title = div.selectFirst("h3")?.text().orEmpty(),
				altTitles = emptySet(),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				authors = emptySet(),
				state = null,
				source = source,
				contentRating = sourceContentRating,
			)
		}
	}

	protected open val selectTagMap = "div.panel-genres-list a:not(.genres-select)"

	protected open suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		val tags = doc.select(selectTagMap).drop(1) // remove all tags
		return tags.mapToSet { a ->
			val key = a.attr("href").removeSuffix('/').substringAfterLast('/')
			val name = a.attr("title").replace(" Manga", "")
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}
	}

	protected open val selectDesc = "div#noidungm, div#panel-story-info-description"
	protected open val selectState = "li:contains(status), td:containsOwn(status) + td"
	protected open val selectAlt = ".story-alternative, tr:has(.info-alternative) h2"
	protected open val selectAut = "li:contains(author) a, td:contains(author) + td a"
	protected open val selectTag = "div.manga-info-top li:contains(genres) a , td:containsOwn(genres) + td a"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(doc) }
		val desc = doc.selectFirst(selectDesc)?.html()
		val stateDiv = doc.select(selectState).text()
		val state = stateDiv.let {
			when (it.lowercase()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}
		val alt = doc.body().select(selectAlt).text().replace("Alternative : ", "").nullIfEmpty()
		val authors = doc.body().select(selectAut).mapToSet { it.text() }

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
			authors = authors,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}

	protected open val selectDate = "span"
	protected open val selectChapter = "div.chapter-list div.row, ul.row-content-chapter li"

	protected open suspend fun getChapters(doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.select(selectDate).last()?.text()

			MangaChapter(
				id = generateUid(href),
				title = a.text(),
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	protected open val selectPage = "div#vungdoc img, div.container-chapter-reader img"

	protected open val otherDomain = ""

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		if (doc.select(selectPage).isNullOrEmpty()) {
			val fullUrl2 = chapter.url.toAbsoluteUrl(domain).replace(domain, otherDomain)
			val doc2 = webClient.httpGet(fullUrl2).parseHtml()

			return doc2.select(selectPage).map { img ->
				val url = img.requireSrc().toRelativeUrl(domain)

				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		} else {
			return doc.select(selectPage).map { img ->
				val url = img.requireSrc().toRelativeUrl(domain)

				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		}

	}

	protected fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {
			WordSet(" ago", " h", " d").endsWith(d) -> {
				parseRelativeDate(d)
			}

			WordSet("today").startsWith(d) -> {
				Calendar.getInstance().apply {
					set(Calendar.HOUR_OF_DAY, 0)
					set(Calendar.MINUTE, 0)
					set(Calendar.SECOND, 0)
					set(Calendar.MILLISECOND, 0)
				}.timeInMillis
			}

			date.contains(Regex("""\d(st|nd|rd|th)""")) -> date.split(" ").map {
				if (it.contains(Regex("""\d\D\D"""))) {
					it.replace(Regex("""\D"""), "")
				} else {
					it
				}
			}.let { dateFormat.tryParse(it.joinToString(" ")) }

			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet("second")
				.anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis

			WordSet("min", "minute", "minutes")
				.anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis

			WordSet("hour", "hours", "h")
				.anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis

			WordSet("day", "days")
				.anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("month", "months")
				.anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis

			WordSet("year")
				.anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis

			else -> 0
		}
	}

}
