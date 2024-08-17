package org.koitharu.kotatsu.parsers.site.keyoapp

import androidx.collection.scatterSetOf
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

internal abstract class KeyoappParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 24,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val isMultipleTagsSupported = false

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.NEWEST,
	)

	protected open val listUrl = "series/"
	protected open val datePattern = "MMM d, yyyy"


	@JvmField
	protected val ongoing = scatterSetOf(
		"ongoing",
	)

	@JvmField
	protected val finished = scatterSetOf(
		"completed",
	)

	@JvmField
	protected val paused = scatterSetOf(
		"paused",
	)

	@JvmField
	protected val upcoming = scatterSetOf(
		"dropped",
	)

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {

		var query = ""
		var tag = ""

		if (page > 1) {
			return emptyList()
		}

		val url = urlBuilder().apply {

			when (filter) {
				is MangaListFilter.Search -> {
					addPathSegment("series")
					query = filter.query
				}

				is MangaListFilter.Advanced -> {

					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							tag = it.title
						}
					}

					when (filter.sortOrder) {
						SortOrder.UPDATED -> addPathSegment("latest")
						SortOrder.NEWEST -> addPathSegment("series")
						else -> addPathSegment("latest")
					}

				}

				null -> addPathSegment("latest")
			}
		}.build()

		return parseMangaList(webClient.httpGet(url).parseHtml(), tag, query)
	}


	protected open fun parseMangaList(doc: Document, tag: String, query: String): List<Manga> {

		val manga = ArrayList<Manga>()

		doc.select("#searched_series_page button").ifEmpty {
			doc.select("div.grid > div.group")
		}.map { div ->

			val title = div.selectFirstOrThrow("h3").text().orEmpty()
			if (query.isNotEmpty() && title.contains(query, ignoreCase = true)) {
				manga.add(addManga(div))
			}

			// Not all tags are present in UPDATED
			val tags = div.attr("tags") ?: div.select("div.gap-1 a").joinToString()
			if (tag.isNotEmpty() && tags.contains(tag, ignoreCase = true)) {
				manga.add(addManga(div))
			}

			if (query.isEmpty() && tag.isEmpty()) {
				manga.add(addManga(div))
			}

		}

		return manga
	}


	private fun addManga(div: Element): Manga {
		val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
		val cover = div.selectFirst("div.h-full") ?: div.selectFirst("a")
		return Manga(
			id = generateUid(href),
			url = href,
			publicUrl = href.toAbsoluteUrl(div.host ?: domain),
			coverUrl = cover?.styleValueOrNull("background-image")?.cssUrl().orEmpty(),
			title = div.selectFirstOrThrow("h3").text().orEmpty(),
			altTitle = null,
			rating = RATING_UNKNOWN,
			tags = div.select("div.gap-1 a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('='),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			author = null,
			state = null,
			source = source,
			isNsfw = isNsfwSource,
		)
	}

	private fun String.cssUrl(): String? {
		val fromIndex = indexOf("url(")
		if (fromIndex == -1) {
			return null
		}
		val toIndex = indexOf(')', startIndex = fromIndex)
		return if (toIndex == -1) {
			null
		} else {
			substring(fromIndex + 4, toIndex).trim()
		}
	}


	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		return doc.requireElementById("series_tags_page").select("button").mapNotNullToSet { button ->
			val key = button.attr("tag") ?: return@mapNotNullToSet null
			val name = button.text().toTitleCase(sourceLocale)
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}
	}

	protected open val selectDesc = "div.grid > div.overflow-hidden > p"
	protected open val selectState = "div[alt=Status]"
	protected open val selectTag = "div.grid:has(>h1) > div > a"
	protected open val selectAuthor = "div[alt=Author]"
	protected open val selectChapter = "#chapters > a:not(:has(.text-sm span:matches(Upcoming)))"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		manga.copy(
			tags = doc.body().select(selectTag).mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('='),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = doc.selectFirstOrThrow(selectDesc).html(),
			state = when (
				doc.selectFirstOrThrow(selectState).text().lowercase()
			) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				in paused -> MangaState.PAUSED
				in upcoming -> MangaState.UPCOMING
				else -> null
			},
			chapters = doc.select(selectChapter)
				.mapChapters(reversed = true) { i, a ->
					val href = a.attrAsRelativeUrl("href")
					val name = a.selectFirstOrThrow("span.truncate").text()
					val dateText = a.selectLast("div.text-xs.w-fit")?.text() ?: "0"
					MangaChapter(
						id = generateUid(href),
						name = name,
						number = i + 1f,
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = parseChapterDate(
							dateFormat,
							dateText,
						),
						branch = null,
						source = source,
					)
				},
		)
	}

	protected open val selectPage = "#pages > img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPage).map { img ->
			val url = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
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
			d.endsWith(" ago") -> parseRelativeDate(date)

			d.startsWith("year") -> Calendar.getInstance().apply {
				add(Calendar.DAY_OF_MONTH, -1)
				set(Calendar.HOUR_OF_DAY, 0)
				set(Calendar.MINUTE, 0)
				set(Calendar.SECOND, 0)
				set(Calendar.MILLISECOND, 0)
			}.timeInMillis

			d.startsWith("today") -> Calendar.getInstance().apply {
				set(Calendar.HOUR_OF_DAY, 0)
				set(Calendar.MINUTE, 0)
				set(Calendar.SECOND, 0)
				set(Calendar.MILLISECOND, 0)
			}.timeInMillis

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
			WordSet("second").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis

			WordSet("minute", "minutes").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis

			WordSet("hour", "hours").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis

			WordSet("day", "days").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("month", "months").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis

			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}
}
