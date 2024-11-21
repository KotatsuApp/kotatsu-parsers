package org.koitharu.kotatsu.parsers.site.zmanga

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

internal abstract class ZMangaParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 16,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.RATING,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isYearSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.ONE_SHOT,
			ContentType.DOUJINSHI,
		),
	)

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}


	@JvmField
	protected val ongoing: Set<String> = setOf(
		"On Going",
		"Ongoing",
	)

	@JvmField
	protected val finished: Set<String> = setOf(
		"Completed",
	)

	protected open val listUrl = "advanced-search/"
	protected open val datePattern = "MMMM d, yyyy"

	// https://komikindo.info/advanced-search/?title=the&author=the&artist=the&yearx=2020&status=ongoing&type=Manga&order=update

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append('/')
			append(listUrl)
			if (page > 1) {
				append("page/")
				append(page.toString())
				append('/')
			}

			append("?order=")
			when (order) {
				SortOrder.POPULARITY -> append("popular")
				SortOrder.UPDATED -> append("update")
				SortOrder.ALPHABETICAL -> append("title")
				SortOrder.ALPHABETICAL_DESC -> append("titlereverse")
				SortOrder.NEWEST -> append("latest")
				SortOrder.RATING -> append("rating")
				else -> append("update")
			}

			filter.query?.let {
				append("&title=")
				append(filter.query.urlEncoded())
			}

			// author
			// filter.author?.let {
			// 	append("&author=")
			// 	append(filter.author.urlEncoded())
			// }

			// artist
			// filter.artist?.let {
			// 	append("&artist=")
			// 	append(filter.artist.urlEncoded())
			// }

			if (filter.year != 0) {
				append("&yearx=")
				append(filter.year)
			}

			filter.types.oneOrThrowIfMany()?.let {
				append("&type=")
				append(
					when (it) {
						ContentType.MANGA -> "Manga"
						ContentType.MANHWA -> "Manhwa"
						ContentType.MANHUA -> "Manhua"
						ContentType.ONE_SHOT -> "One-shot"
						ContentType.DOUJINSHI -> "Doujinshi"
						else -> ""
					},
				)
			}

			filter.tags.forEach {
				append("&")
				append("genre[]".urlEncoded())
				append("=")
				append(it.key)
			}

			filter.states.oneOrThrowIfMany()?.let {
				append("&status=")
				append(
					when (it) {
						MangaState.ONGOING -> "ongoing"
						MangaState.FINISHED -> "completed"
						else -> ""
					},
				)
			}
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.flexbox2-item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("div.flexbox2-title span:not(.studio)").text().orEmpty(),
				altTitle = null,
				rating = div.selectFirstOrThrow("div.info div.score").ownText().toFloatOrNull()?.div(10f)
					?: RATING_UNKNOWN,
				tags = doc.body().select("div.genres a").mapToSet { span ->
					MangaTag(
						key = span.attr("class"),
						title = span.text().toTitleCase(),
						source = source,
					)
				},
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	protected open suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		return doc.select("tr.gnrx div.custom-control").mapNotNullToSet { checkbox ->
			val key = checkbox.selectFirstOrThrow("input").attr("value") ?: return@mapNotNullToSet null
			val name = checkbox.selectFirstOrThrow("label").text()
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}
	}

	protected open val selectDesc = "div.series-synops"
	protected open val selectState = "span.status"
	protected open val selectAlt = "div.series-infolist li:contains(Alt) span"
	protected open val selectAut = "div.series-infolist li:contains(Author) span"
	protected open val selectTag = "div.series-genres a"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val chaptersDeferred = async { getChapters(doc) }

		val desc = doc.selectFirstOrThrow(selectDesc).html()

		val stateDiv = doc.selectFirst(selectState)

		val state = stateDiv?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}

		val alt = doc.body().select(selectAlt).text()

		val aut = doc.body().select(selectAut).text()

		manga.copy(
			tags = doc.body().select(selectTag).mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase().replace(",", ""),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
			author = aut,
			state = state,
			chapters = chaptersDeferred.await(),
			isNsfw = manga.isNsfw || doc.getElementById("adt-warning") != null,
		)
	}


	protected open val selectDate = "span.date"
	protected open val selectChapter = "ul.series-chapterlist li"

	protected open suspend fun getChapters(doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.selectFirst(selectDate)?.text()
			MangaChapter(
				id = generateUid(href),
				name = li.selectFirstOrThrow(".flexch-infoz span:not(.date)").text(),
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

	protected open val selectPage = "div.reader-area img"

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

			WordSet("day", "days").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			WordSet("month", "months")
				.anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis

			WordSet("year")
				.anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis

			else -> 0
		}
	}

}
