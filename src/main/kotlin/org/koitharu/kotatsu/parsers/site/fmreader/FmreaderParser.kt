package org.koitharu.kotatsu.parsers.site.fmreader

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

internal abstract class FmreaderParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 20,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.UPDATED_ASC,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_ASC,
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(
			MangaState.ONGOING,
			MangaState.FINISHED,
			MangaState.ABANDONED,
		),
	)

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	@JvmField
	protected val ongoing: Set<String> = setOf(
		"on going",
		"incomplete",
		"en curso",
	)

	@JvmField
	protected val finished: Set<String> = setOf(
		"completed",
		"completado",
	)

	@JvmField
	protected val abandoned: Set<String> = hashSetOf(
		"canceled",
		"cancelled",
		"drop",
	)

	protected open val listUrl = "/manga-list.html"
	protected open val datePattern = "MMMM d, yyyy"
	protected open val tagPrefix = "manga-list-genre-"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append(listUrl)
			append("?page=")
			append(page.toString())

			filter.query?.let {
				append("&name=")
				append(filter.query.urlEncoded())
			}

			// filter.author?.let {
			// 	append("&author=")
			// 	append(filter.author.urlEncoded())
			// }

			append("&genre=")
			append(filter.tags.joinToString(",") { it.key })

			append("&ungenre=")
			append(filter.tagsExclude.joinToString(",") { it.key })


			append("&sort=")
			when (order) {
				SortOrder.POPULARITY -> append("views&sort_type=DESC")
				SortOrder.POPULARITY_ASC -> append("views&sort_type=ASC")
				SortOrder.UPDATED -> append("last_update&sort_type=DESC")
				SortOrder.UPDATED_ASC -> append("last_update&sort_type=ASC")
				SortOrder.ALPHABETICAL -> append("name&sort_type=ASC")
				SortOrder.ALPHABETICAL_DESC -> append("name&sort_type=DESC")
				else -> append("last_update&sort_type=DESC")
			}

			append("&m_status=")
			filter.states.oneOrThrowIfMany()?.let {
				append(
					when (it) {
						MangaState.ONGOING -> "2"
						MangaState.FINISHED -> "1"
						MangaState.ABANDONED -> "3"
						else -> ""
					},
				)
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())

	}

	protected open fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("div.thumb-item-flow").map { div ->
			val href = div.selectFirstOrThrow("div.series-title a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = (div.selectFirst("div.img-in-ratio")?.attr("data-bg")
					?: div.selectFirst("div.img-in-ratio")?.attr("style")?.substringAfter("(")
						?.substringBefore(")"))?.toAbsoluteUrl(domain).orEmpty(),
				title = div.selectFirst("div.series-title")?.text().orEmpty(),
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

	protected open val selectBodyTag = "ul.filter-type li a"

	protected open suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		return doc.select(selectBodyTag).mapToSet { a ->
			val href = a.attr("href").substringAfter(tagPrefix).substringBeforeLast(".html")
			MangaTag(
				key = href,
				title = a.text().toTitleCase(sourceLocale),
				source = source,
			)
		}
	}

	protected open val selectDesc = "div.summary-content"
	protected open val selectState = "ul.manga-info li:contains(Status) a"
	protected open val selectAlt = "ul.manga-info li:contains(Other names)"
	protected open val selectAut = "ul.manga-info li:contains(Author(s)) a"
	protected open val selectTag = "ul.manga-info li:contains(Genre(s)) a"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(doc) }
		val desc = doc.selectFirst(selectDesc)?.html()
		val stateDiv = doc.selectFirst(selectState)
		val state = stateDiv?.let {
			when (it.text().lowercase()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				in abandoned -> MangaState.ABANDONED
				else -> null
			}
		}
		val alt = doc.body().selectFirst(selectAlt)?.text()?.replace("Other names", "")
		val auth = doc.body().selectFirst(selectAut)?.text()
		manga.copy(
			tags = doc.body().select(selectTag).mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfter(tagPrefix).substringBeforeLast(".html"),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
			author = auth,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}


	protected open val selectDate = "div.chapter-time"
	protected open val selectChapter = "ul.list-chapters a"

	protected open suspend fun getChapters(doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, a ->
			val href = a.attrAsRelativeUrl("href")
			val dateText = a.selectFirst(selectDate)?.text()
			MangaChapter(
				id = generateUid(href),
				name = a.selectFirstOrThrow("div.chapter-name").text(),
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

	protected open val selectPage = "div.chapter-content img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
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

	protected fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {

			WordSet(" ago", " atrás", " h", " d").endsWith(d) -> {
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

			WordSet("min", "minute", "minutes", "minuto", "minutos")
				.anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis

			WordSet("hour", "hours", "hora", "horas", "h")
				.anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis

			WordSet("day", "days", "día", "dia")
				.anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("week", "weeks", "semana", "semanas")
				.anyWordIn(date) -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis

			WordSet("month", "months", "mes", "meses")
				.anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis

			WordSet("year", "año", "años")
				.anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis

			else -> 0
		}
	}
}
