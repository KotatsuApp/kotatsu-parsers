package org.koitharu.kotatsu.parsers.site.wpcomics

import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

internal abstract class WpComicsParser(
	context: MangaLoaderContext,
	source: MangaSource,
	domain: String,
	pageSize: Int = 48,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.RATING,
	)

	override val availableStates: Set<MangaState> = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED)

	protected open val listUrl = "/tim-truyen-nang-cao"
	protected open val datePattern = "dd/MM/yy"


	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}


	@JvmField
	protected val ongoing: Set<String> = setOf(
		"Đang tiến hành",
		"Ongoing",
	)

	@JvmField
	protected val finished: Set<String> = setOf(
		"Hoàn thành",
		"Completed",
	)

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val response =
			when (filter) {
				is MangaListFilter.Search -> {
					val url = buildString {
						append("https://")
						append(domain)
						append("/tim-truyen?keyword=")
						append(filter.query.urlEncoded())
						append("&page=")
						append(page.toString())
					}

					val result = runCatchingCancellable { webClient.httpGet(url) }
					val exception = result.exceptionOrNull()
					if (exception is NotFoundException) {
						return emptyList()
					}
					result.getOrThrow()
				}

				is MangaListFilter.Advanced -> {
					val url = buildString {
						append("https://")
						append(domain)
						val tagQuery = filter.tags.joinToString(",") { it.key }
						append("/tim-truyen-nang-cao?genres=")
						append(tagQuery)
						append("&notgenres=&gender=-1&minchapter=1&sort=")
						append(
							when (filter.sortOrder) {
								SortOrder.UPDATED -> 0
								SortOrder.POPULARITY -> 10
								SortOrder.NEWEST -> 15
								SortOrder.RATING -> 20
								else -> throw IllegalArgumentException("Sort order ${filter.sortOrder.name} not supported")
							},
						)
						filter.states.oneOrThrowIfMany()?.let {
							append("&status=")
							append(
								when (it) {
									MangaState.ONGOING -> "1"
									MangaState.FINISHED -> "2"
									else -> "-1"
								},
							)
						}
						append("&page=")
						append(page.toString())
					}

					webClient.httpGet(url)
				}

				null -> {
					val url = buildString {
						append("https://")
						append(domain)
						append("/tim-truyen-nang-cao?genres=&notgenres=&gender=-1&status=-1&minchapter=1&sort=0&page=")
						append(page.toString())
					}
					webClient.httpGet(url)
				}
			}

		val itemsElements = response.parseHtml()
			.select("div.ModuleContent > div.items")
			.select("div.item")
		return itemsElements.mapNotNull { item ->
			val tooltipElement = item.selectFirst("div.box_tootip") ?: return@mapNotNull null
			val absUrl = item.selectFirst("div.image > a")?.attrAsAbsoluteUrlOrNull("href") ?: return@mapNotNull null
			val slug = absUrl.substringAfterLast('/')
			val mangaState = when (tooltipElement.selectFirst("div.message_main > p:contains(Tình trạng)")?.ownText()) {
				"Đang tiến hành" -> MangaState.ONGOING
				"Hoàn thành" -> MangaState.FINISHED
				else -> null
			}

			val tagMap = getOrCreateTagMap()
			val tagsElement = tooltipElement.selectFirst("div.message_main > p:contains(Thể loại)")?.ownText().orEmpty()
			val mangaTags = tagsElement.split(',').mapNotNullToSet { tagMap[it.trim()] }
			Manga(
				id = generateUid(slug),
				title = tooltipElement.selectFirst("div.title")?.text().orEmpty(),
				altTitle = null,
				url = absUrl.toRelativeUrl(domain),
				publicUrl = absUrl,
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = item.selectFirst("div.image a img")?.absUrl("data-original").orEmpty(),
				largeCoverUrl = null,
				tags = mangaTags,
				state = mangaState,
				author = tooltipElement.selectFirst("div.message_main > p:contains(Tác giả)")?.ownText(),
				description = tooltipElement.selectFirst("div.box_text")?.text(),
				chapters = null,
				source = source,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val map = getOrCreateTagMap()
		val tagSet = ArraySet<MangaTag>(map.size)
		for (entry in map) {
			tagSet.add(entry.value)
		}
		return tagSet
	}


	private val mutex = Mutex()
	private var tagCache: ArrayMap<String, MangaTag>? = null

	private suspend fun getOrCreateTagMap(): ArrayMap<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val doc = webClient.httpGet("/tim-truyen-nang-cao".toAbsoluteUrl(domain)).parseHtml()
		val tagItems = doc.select("div.genre-item")
		val result = ArrayMap<String, MangaTag>(tagItems.size)
		for (item in tagItems) {
			val title = item.text()
			val key = item.select("span[data-id]").attr("data-id")
			if (key.isNotEmpty() && title.isNotEmpty()) {
				result[title] = MangaTag(title = title, key = key, source = source)
			}
		}
		tagCache = result
		result
	}

	protected open val selectDesc = "div.detail-content p"
	protected open val selectState = "div.col-info li.status p:not(.name)"
	protected open val selectAut = "div.col-info li.author p:not(.name)"
	protected open val selectTag = "div.col-info li.kind p:not(.name) a"

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
		val aut = doc.body().select(selectAut).text()
		manga.copy(
			description = desc,
			altTitle = null,
			author = aut,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}


	protected open val selectDate = "div.col-xs-4"
	protected open val selectChapter = "div#nt_listchapter li .chapter"

	protected open suspend fun getChapters(doc: Document): List<MangaChapter> {
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.selectFirst(selectDate)?.text()
			val findHours = dateText?.contains(":")
			val dateFormat = if (findHours == true) {
				SimpleDateFormat("HH:mm dd/MM", sourceLocale)
			} else {
				SimpleDateFormat(datePattern, sourceLocale)
			}
			MangaChapter(
				id = generateUid(href),
				name = a.text(),
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


	protected open val selectPage = "div.reading-detail img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()


		return doc.select(selectPage).map { url ->
			val img = url.src()?.toRelativeUrl(domain) ?: url.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(img),
				url = img,
				preview = null,
				source = source,
			)
		}
	}

	protected fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		// Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
		val d = date?.lowercase() ?: return 0
		return when {
			d.endsWith(" ago") ||
				d.endsWith(" trước")  // Handle translated 'ago' in Viêt Nam.
			-> parseRelativeDate(date)

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
				"d",
				"ngày ",
			).anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("jam", "saat", "heure", "hora", "horas", "hour", "hours", "h").anyWordIn(date) -> cal.apply {
				add(
					Calendar.HOUR,
					-number,
				)
			}.timeInMillis

			WordSet(
				"min",
				"minute",
				"minutes",
				"mins",
				"phút",
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
