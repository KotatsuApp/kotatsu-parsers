package org.koitharu.kotatsu.parsers.site.madtheme

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

internal abstract class MadthemeParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 48,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
		SortOrder.NEWEST,
		SortOrder.RATING,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
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
		"on going",
		"ongoing",
	)

	@JvmField
	protected val finished: Set<String> = setOf(
		"completed",
	)

	protected open val listUrl = "search/"
	protected open val datePattern = "MMM dd, yyyy"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append('/')
			append(listUrl)

			append("?page=")
			append(page.toString())

			filter.query?.let {
				append("&q=")
				append(filter.query.urlEncoded())
			}

			append("&sort=")
			when (order) {
				SortOrder.POPULARITY -> append("views")
				SortOrder.UPDATED -> append("updated_at")
				SortOrder.ALPHABETICAL -> append("name") // On some sites without tags or searches, the alphabetical option is empty.
				SortOrder.NEWEST -> append("created_at")
				SortOrder.RATING -> append("rating")
				else -> append("updated_at")
			}
			if (filter.tags.isNotEmpty()) {
				filter.tags.forEach {
					append("&")
					append("genre[]".urlEncoded())
					append("=")
					append(it.key)
				}
			}

			filter.states.oneOrThrowIfMany()?.let {
				append("&status=")
				append(
					when (it) {
						MangaState.ONGOING -> "ongoing"
						MangaState.FINISHED -> "completed"
						else -> "all"
					},
				)
			}
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.book-item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst("div.meta")?.selectFirst("div.title")?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirst("div.meta span.score")?.ownText()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
				tags = doc.body().select("div.meta div.genres span").mapToSet { span ->
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

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		return doc.select("div.genres .checkbox").mapNotNullToSet { checkbox ->
			val key = checkbox.selectFirstOrThrow("input").attr("value") ?: return@mapNotNullToSet null
			val name = checkbox.selectFirst("span.radio__label")?.text() ?: key
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}
	}

	protected open val selectDesc = "div.section-body.summary p.content"
	protected open val selectState = "div.detail p:contains(Status) span"
	protected open val selectAlt = "div.detail div.name h2"
	protected open val selectTag = "div.detail p:contains(Genres) a"

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
				else -> null
			}
		}

		val alt = doc.body().select(selectAlt).text()

		val nsfw = doc.getElementById("adt-warning") != null

		manga.copy(
			tags = doc.body().select(selectTag).mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase().replace(",", ""),
					source = source,
				)
			},
			description = desc.orEmpty(),
			altTitle = alt.orEmpty(),
			state = state,
			chapters = chaptersDeferred.await(),
			isNsfw = nsfw || manga.isNsfw,
		)
	}


	protected open val selectDate = "div .chapter-update"
	protected open val selectChapter = "ul#chapter-list li"

	protected open suspend fun getChapters(doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		val slug = doc.selectFirstOrThrow("script:containsData(bookSlug)").data().substringAfter("bookSlug = \"")
			.substringBefore("\";")
		val docChapter = webClient.httpGet("https://$domain/api/manga/$slug/chapters?source=detail").parseHtml()
		return docChapter.select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.selectFirst(selectDate)?.text()
			MangaChapter(
				id = generateUid(href),
				name = li.selectFirst(".chapter-title")?.text() ?: "Chapters : ${i + 1f}",
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

	protected open val selectPage = "div#chapter-images img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val known = HashSet<String>()
		val result = ArrayList<MangaPage>()
		// html parisng
		doc.select(selectPage).forEach { img ->
			val url = img.requireSrc().toRelativeUrl(domain)
			if (known.add(url)) {
				result += MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		}
		// js parsing
		val regexPages = Regex("chapImages\\s*=\\s*['\"](.*?)['\"]")
		val pages = doc.select("script").firstNotNullOfOrNull { script ->
			regexPages.find(script.html())?.groupValues?.getOrNull(1)
		}?.split(',')
		pages?.forEach { url ->
			if (known.add(url)) {
				result += MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		}
		return result
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
