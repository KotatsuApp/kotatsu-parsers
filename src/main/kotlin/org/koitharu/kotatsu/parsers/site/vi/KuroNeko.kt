package org.koitharu.kotatsu.parsers.site.vi

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("KURONEKO", "Kuro Neko / vi-Hentai", "vi", type = ContentType.HENTAI)
internal class KuroNeko(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.KURONEKO, 30) {

	override val configKeyDomain = ConfigKey.Domain("vi-hentai.moe", "vi-hentai.org")

	private val pagesRequestMutex = Mutex()
	private var lastPagesRequestTime = 0L

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchWithFiltersSupported = true,
			isAuthorSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = availableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			if (!filter.author.isNullOrEmpty()) {
				clear()
				append("https://")
				append(domain)

				append("/tac-gia/")
				append(filter.author.lowercase().replace(" ", "-"))

				append("?sort=")
				append(
					when (order) {
						SortOrder.POPULARITY -> "-views"
						SortOrder.UPDATED -> "-updated_at"
						SortOrder.NEWEST -> "-created_at"
						SortOrder.ALPHABETICAL -> "name"
						SortOrder.ALPHABETICAL_DESC -> "-name"
						else -> "-updated_at"
					},
				)

				append("&page=")
				append(page)

				append("&filter[status]=")
				filter.states.forEach {
					append(
						when (it) {
							MangaState.ONGOING -> "2,"
							MangaState.FINISHED -> "1,"
							else -> "2,1"
						},
					)
				}

				return@buildString // end of buildString
			}

			append("https://")
			append(domain)

			append("/tim-kiem")
			append("?sort=")
			append(
				when (order) {
					SortOrder.POPULARITY -> "-views"
					SortOrder.UPDATED -> "-updated_at"
					SortOrder.NEWEST -> "-created_at"
					SortOrder.ALPHABETICAL -> "name"
					SortOrder.ALPHABETICAL_DESC -> "-name"
					else -> "-updated_at"
				},
			)

			if (!filter.query.isNullOrEmpty()) {
				append("&keyword=")
				append(filter.query.urlEncoded())
			}

			if (page > 1) {
				append("&page=")
				append(page)
			}

			append("&filter[status]=")
			filter.states.forEach {
				append(
					when (it) {
						MangaState.ONGOING -> "2,"
						MangaState.FINISHED -> "1,"
						else -> "2,1"
					},
				)
			}

			if (filter.tags.isNotEmpty()) {
				append("&filter[accept_genres]=")
				filter.tags.joinTo(this, separator = ",") { it.key }
			}

			if (filter.tagsExclude.isNotEmpty()) {
				append("&filter[reject_genres]=")
				filter.tagsExclude.joinTo(this, separator = ",") { it.key }
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.grid div.relative")
			.map { div ->
				val href = div.selectFirst("a[href^=/truyen/]")?.attrOrNull("href")
					?: div.parseFailed("Không thể tìm thấy nguồn ảnh của Manga này!")
				val coverUrl = div.selectFirst("div.cover")?.attr("style")
					?.substringAfter("url('")?.substringBefore("')")

				Manga(
					id = generateUid(href),
					title = div.select("div.p-2 a.text-ellipsis").text(),
					altTitles = emptySet(),
					url = href,
					publicUrl = href.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					contentRating = ContentRating.ADULT,
					coverUrl = coverUrl.orEmpty(),
					tags = setOf(),
					state = null,
					authors = emptySet(),
					source = source,
				)
			}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val author = root.selectFirst("div.mt-2:contains(Tác giả) span a")?.textOrNull()

		return manga.copy(
			altTitles = setOfNotNull(root.selectLast("div.grow div:contains(Tên khác) span")?.textOrNull()),
			state = when (root.selectFirst("div.mt-2:contains(Tình trạng) span.text-blue-500")?.text()) {
				"Đang tiến hành" -> MangaState.ONGOING
				"Đã hoàn thành" -> MangaState.FINISHED
				else -> null
			},
			tags = root.select("div.mt-2:contains(Thể loại) a.bg-gray-500").mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text(),
					source = source,
				)
			},
			authors = setOfNotNull(author),
			description = root.selectFirst("meta[name=description]")?.attrOrNull("content"),
			chapters = root.select("div.justify-between ul.overflow-y-auto.overflow-x-hidden a")
				.mapChapters(reversed = true) { i, a ->
					val href = a.attrAsRelativeUrl("href")
					val name = a.selectFirst("span.text-ellipsis")?.text().orEmpty()
					val dateText = a.parent()?.selectFirst("span.timeago")?.attr("datetime").orEmpty()
					val scanlator = root.selectFirst("div.mt-2:contains(Nhóm dịch) span a")?.textOrNull()
					MangaChapter(
						id = generateUid(href),
						title = name,
						number = i.toFloat(),
						volume = 0,
						url = href,
						scanlator = scanlator,
						uploadDate = parseDateTime(dateText),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		pagesRequestMutex.withLock {
			val currentTime = System.currentTimeMillis()
			val timeSinceLastRequest = currentTime - lastPagesRequestTime
			if (timeSinceLastRequest < PAGES_REQUEST_DELAY_MS) {
				delay(PAGES_REQUEST_DELAY_MS - timeSinceLastRequest)
			}
			lastPagesRequestTime = System.currentTimeMillis()
		}

		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("div.text-center img").mapNotNull { img ->
			val url = img.requireSrc()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun availableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/tim-kiem").parseHtml()
		val regex = Regex("toggleGenre\\('([0-9]+)'\\)")
		return doc.select("div.grid.grid-cols-3 label").mapNotNullToSet { label ->
			val attr = label.attr("@click")
			val number = attr.findGroupValue(regex) ?: return@mapNotNullToSet null
			MangaTag(
				key = number,
				title = label.text(),
				source = source,
			)
		}.toSet()
	}

	private fun parseDateTime(dateStr: String): Long = runCatching {
		val parts = dateStr.split(' ')
		val dateParts = parts[0].split('-')
		val timeParts = parts[1].split(':')

		val calendar = Calendar.getInstance()
		calendar.set(
			dateParts[0].toInt(),
			dateParts[1].toInt() - 1,
			dateParts[2].toInt(),
			timeParts[0].toInt(),
			timeParts[1].toInt(),
			timeParts[2].toInt(),
		)
		calendar.timeInMillis
	}.getOrDefault(0L)

	companion object {
		private const val PAGES_REQUEST_DELAY_MS = 5000L
	}
}
