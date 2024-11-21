package org.koitharu.kotatsu.parsers.site.keyoapp

import androidx.collection.scatterSetOf
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.SinglePageMangaParser
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
) : SinglePageMangaParser(context, source) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

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

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		var query = ""
		var tag = ""

		val url = urlBuilder().apply {

			filter.query?.let {
				query = filter.query
			}

			filter.tags.oneOrThrowIfMany()?.let {
				tag = it.title
			}

			when (order) {
				SortOrder.UPDATED -> addPathSegment("latest")
				SortOrder.NEWEST -> addPathSegment("series")
				else -> addPathSegment("series")
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
			tags = div.select("div.gap-1 a").mapToSet { a ->
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

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
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
			tags = doc.body().select(selectTag).mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('='),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = doc.selectFirst(selectDesc)?.html().orEmpty(),
			state = when (
				doc.selectFirst(selectState)?.text()?.lowercase().orEmpty()
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

		val cdnUrl = getCdnUrl(doc)
		doc.select(selectPage)
			.map { it.attr("uid") }
			.filter { it.isNotEmpty() }
			.also { cdnUrl ?: throw Exception("Image url not found") }
			.map { img ->
				val url = "$cdnUrl/$img"
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
			.takeIf { it.isNotEmpty() }
			?.also { return it }

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

	protected open val cdnRegex = """realUrl\s*=\s*`[^`]+//(?<host>[^/]+)""".toRegex()

	protected open fun getCdnUrl(document: Document): String? {
		return document.select("script")
			.firstOrNull { cdnRegex.containsMatchIn(it.html()) }
			?.let {
				val cdnHost = cdnRegex.find(it.html())
					?.groups?.get("host")?.value
					?.replace(cdnRegex, "")
				"https://$cdnHost/uploads"
			}
	}

	protected fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {

			WordSet(" ago").endsWith(d) -> {
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

			WordSet("minute", "minutes")
				.anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis

			WordSet("hour", "hours")
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
