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
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.RATING,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
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
		"Đang tiến hành",
		"Đang cập nhật",
		"Ongoing",
		"Updating",
		"連載中",
	)

	@JvmField
	protected val finished: Set<String> = setOf(
		"Hoàn thành",
		"Đã hoàn thành",
		"Complete",
		"Completed",
		"完結済み",
	)

	protected open val listUrl = "/tim-truyen"
	protected open val datePattern = "dd/MM/yy"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val response =
			when {
				!filter.query.isNullOrEmpty() -> {
					val url = buildString {
						append("https://")
						append(domain)
						append(listUrl)
						append("?keyword=")
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

				else -> {
					val url = buildString {
						append("https://")
						append(domain)
						append(listUrl)
						if (filter.tags.isNotEmpty()) {
							append('/')
							filter.tags.oneOrThrowIfMany()?.let {
								append(it.key)
							}
						}
						append("?sort=")
						append(
							when (order) {
								SortOrder.UPDATED -> 0
								SortOrder.POPULARITY -> 10
								SortOrder.NEWEST -> 15
								SortOrder.RATING -> 20
								else -> throw IllegalArgumentException("Sort order ${order.name} not supported")
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
			}

		val tagMap = getOrCreateTagMap()
		return parseMangaList(response.parseHtml(), tagMap)
	}

	protected open fun parseMangaList(doc: Document, tagMap: ArrayMap<String, MangaTag>): List<Manga> {
		return doc.select("div.items div.item").mapNotNull { item ->
			val tooltipElement = item.selectFirst("div.box_tootip")
			val absUrl = item.selectFirst("div.image > a")?.attrAsAbsoluteUrlOrNull("href") ?: return@mapNotNull null
			val slug = absUrl.substringAfterLast('/')
			val mangaState =
				when (tooltipElement?.selectFirst("div.message_main > p:contains(Tình trạng)")?.ownText()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				}
			val tagsElement =
				tooltipElement?.selectFirst("div.message_main > p:contains(Thể loại)")?.ownText().orEmpty()
			val mangaTags = tagsElement.split(',').mapNotNullToSet { tagMap[it.trim()] }
			Manga(
				id = generateUid(slug),
				title = item.selectFirst("div.box_tootip div.title, h3 a")?.text().orEmpty(),
				altTitle = null,
				url = absUrl.toRelativeUrl(domain),
				publicUrl = absUrl,
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = item.selectFirst("div.image a img")?.absUrl("data-original").orEmpty(),
				largeCoverUrl = null,
				tags = mangaTags,
				state = mangaState,
				author = tooltipElement?.selectFirst("div.message_main > p:contains(Tác giả)")?.ownText(),
				description = tooltipElement?.selectFirst("div.box_text")?.text(),
				chapters = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val map = getOrCreateTagMap()
		val tagSet = ArraySet<MangaTag>(map.size)
		for (entry in map) {
			tagSet.add(entry.value)
		}
		return tagSet
	}

	protected open val mutex = Mutex()
	protected open var tagCache: ArrayMap<String, MangaTag>? = null

	protected open suspend fun getOrCreateTagMap(): ArrayMap<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val doc = webClient.httpGet(listUrl.toAbsoluteUrl(domain)).parseHtml()
		val tagItems = doc.select("div.dropdown-genres select option")
		val result = ArrayMap<String, MangaTag>(tagItems.size)
		for (item in tagItems) {
			val title = item.text()
			val key = item.attr("value").substringAfterLast('/')
			if (key.isNotEmpty() && title.isNotEmpty()) {
				result[title] = MangaTag(title = title, key = key, source = source)
			}
		}
		tagCache = result
		result
	}

	protected open val selectDesc = "div.detail-content p"
	protected open val selectState = "div.col-info li.status p:not(.name)"
	protected open val selectAut = "div.col-info li.author p:not(.name), li.author p.col-xs-8"
	protected open val selectTag = "div.col-info li.kind p:not(.name) a, li.kind p.col-xs-8 a"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(doc) }
		val tagMap = getOrCreateTagMap()
		val tagsElement = doc.select("li.kind p.col-xs-8 a")
		val mangaTags = tagsElement.mapNotNullToSet { tagMap[it.text()] }
		manga.copy(
			description = doc.selectFirst(selectDesc)?.html().orEmpty(),
			altTitle = doc.selectFirst("h2.other-name")?.text().orEmpty(),
			author = doc.body().select(selectAut).text(),
			state = doc.selectFirst(selectState)?.let {
				when (it.text()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				}
			},
			tags = mangaTags,
			rating = doc.selectFirst("div.star input")?.attr("value")?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
			chapters = chaptersDeferred.await(),
		)
	}


	protected open val selectDate = "div.col-xs-4"
	protected open val selectChapter = "div.list-chapter li.row:not(.heading)"

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

	protected open val selectPage = "div.page-chapter > img, li.blocks-gallery-item img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPage).map { url ->
			val img = url.requireSrc().toRelativeUrl(domain)
			MangaPage(
				id = generateUid(img),
				url = img,
				preview = null,
				source = source,
			)
		}
	}

	protected fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {

			WordSet(" ago", " trước").endsWith(d) -> {
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
			WordSet("second", "giây")
				.anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis

			WordSet("min", "minute", "minutes", "mins", "phút")
				.anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis

			WordSet("jam", "saat", "heure", "hora", "horas", "hour", "hours", "h", "giờ")
				.anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis

			WordSet("day", "days", "d", "ngày")
				.anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("month", "months", "tháng")
				.anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis

			WordSet("year", "năm").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}
}
