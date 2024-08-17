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

	override val availableStates: Set<MangaState> = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED)

	protected open val listUrl = "search/"
	protected open val datePattern = "MMM dd, yyyy"


	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}


	@JvmField
	protected val ongoing: Set<String> = setOf(
		"On Going",
		"Ongoing",
		"ONGOING",
	)

	@JvmField
	protected val finished: Set<String> = setOf(
		"Completed",
		"COMPLETED",
	)

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append('/')
			append(listUrl)
			when (filter) {

				is MangaListFilter.Search -> {
					append("?sort=updated_at&q=")
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {

					append("?sort=")
					when (filter.sortOrder) {
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

				null -> append("?sort=updated_at")
			}

			append("&page=")
			append(page.toString())
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.book-item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("div.meta").selectFirst("div.title")?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirstOrThrow("div.meta span.score").ownText().toFloatOrNull()?.div(5f)
					?: RATING_UNKNOWN,
				tags = doc.body().select("div.meta div.genres span").mapNotNullToSet { span ->
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

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		return doc.select("div.genres .checkbox").mapNotNullToSet { checkbox ->
			val key = checkbox.selectFirstOrThrow("input").attr("value") ?: return@mapNotNullToSet null
			val name = checkbox.selectFirstOrThrow("span.radio__label").text()
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

		val nsfw = doc.getElementById("adt-warning") != null

		manga.copy(
			tags = doc.body().select(selectTag).mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase().replace(",", ""),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
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
				name = li.selectFirstOrThrow(".chapter-title").text(),
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
			val url = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
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
		// Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
		val d = date?.lowercase() ?: return 0
		return when {
			d.endsWith(" ago") ||
				// short Hours
				d.endsWith(" h") ||
				// short Day
				d.endsWith(" d") -> parseRelativeDate(date)

			// Handle 'yesterday' and 'today', using midnight
			d.startsWith("year") -> Calendar.getInstance().apply {
				add(Calendar.DAY_OF_MONTH, -1) // yesterday
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

	// Parses dates in this form:
	// 21 hours ago
	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()

		return when {
			WordSet(
				"day",
				"days",
			).anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("hour", "hours", "h").anyWordIn(date) -> cal.apply {
				add(
					Calendar.HOUR,
					-number,
				)
			}.timeInMillis

			WordSet(
				"min",
				"minute",
				"minutes",
			).anyWordIn(date) -> cal.apply {
				add(
					Calendar.MINUTE,
					-number,
				)
			}.timeInMillis

			WordSet("second").anyWordIn(date) -> cal.apply {
				add(
					Calendar.SECOND,
					-number,
				)
			}.timeInMillis

			WordSet("month", "months").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}

}
