package org.koitharu.kotatsu.parsers.site.vi

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("DAMCONUONG", "Dâm Cô Nương", "vi", type = ContentType.HENTAI)
internal class DamCoNuong(context: MangaLoaderContext) : LegacyPagedMangaParser(context, MangaParserSource.DAMCONUONG, 30) {

	override val configKeyDomain = ConfigKey.Domain("damconuong.fit")

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
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/tim-kiem")

			append("?sort=")
			append(
				when (order) {
					SortOrder.UPDATED -> "-updated_at"
					SortOrder.NEWEST -> "-created_at"
					SortOrder.POPULARITY -> "-views"
					SortOrder.ALPHABETICAL -> "name"
					SortOrder.ALPHABETICAL_DESC -> "-name"
					else -> "-updated_at"
				}
			)

            if (filter.states.isNotEmpty()) {
				append("&filter[status]=")
				filter.states.forEach {
					append(
						when (it) {
							MangaState.ONGOING -> "2"
							MangaState.FINISHED -> "1"
							else -> "2,1"
						},
					)
				}
			}

			if (filter.tags.isNotEmpty()) {
				append("&filter[accept_genres]=")
				append(filter.tags.joinTo(this, ",") { it.key })
			}

			if (!filter.query.isNullOrEmpty()) {
				append("&filter[name]=")
				append(filter.query.urlEncoded())
			}

			append("&page=$page")
		}

		val doc = webClient.httpGet(url).parseHtml()
		return parseMangaList(doc)
	}

	private suspend fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("div.border.rounded-lg.border-gray-300.dark\\:border-dark-blue.bg-white.dark\\:bg-fire-blue.manga-vertical").map { element ->
			val mainA = element.selectFirst("div.relative a")
			val href = mainA?.attr("href") ?: ""
			val title = element.selectFirst("div.latest-chapter a.text-white.capitalize").text() ?: "No name"
			val coverUrl = element.selectFirst("img.rounded-t-lg.cover.lazyload")?.attr("data-src") ?: ""

			Manga(
				id = generateUid(href),
				title = title,
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = coverUrl,
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(url).parseHtml()

		val altTitles = doc.select("div.mt-2:contains(Tên khác:) span").map { it.text() }.toSet().orEmpty()
		val tags = doc.select("div.mt-2:contains(Thể loại:) a").map { a ->
			MangaTag(
				key = "", // Auto-detected by title, idk
				title = a.text(),
				source = source,
			)
		}.toSet()

		val stateText = doc.selectFirst("div.mt-2:contains(Tình trạng:) span")?.text()
		val state = when (stateText) {
			"Đang tiến hành" -> MangaState.ONGOING
			else -> MangaState.FINISHED
		}

		val chapterListDiv = doc.selectFirst("div#chapterList.justify-between.border-2.border-gray-100.dark\\:border-dark-blue.p-3.bg-white.dark\\:bg-fire-blue.shadow-md.rounded.dark\\:shadow-gray-900.mb-4")
			?: throw ParseException("Chapters list not found!", url)

		val chapterLinks = chapterListDiv.select("a.block")
		val chapters = chapterLinks.mapIndexed { index, a ->
			val title = a.selectFirst("span.text-ellipsis")?.text().orEmpty()
			val href = a.attr("href")
			val uploadDate = a.selectFirst("span.ml-2.whitespace-nowrap")?.text().orEmpty()

			MangaChapter(
				id = generateUid(href),
				title = title,
				number = (chapterLinks.size - index).toFloat(),
				volume = 0,
				url = href,
				scanlator = null,
				uploadDate = parseChapterDate(uploadDate),
				branch = null,
				source = source,
			)
		}.reversed()

		return manga.copy(
			altTitles = altTitles,
			tags = tags,
			state = state,
			chapters = chapters,
		)
	}

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("div.text-center img.max-w-full.my-0.mx-auto").mapNotNull { img ->
			val url = img.requireSrc()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

    private fun parseChapterDate(date: String?): Long {
		if (date == null) return 0
		return when {
			date.contains("giây trước") -> System.currentTimeMillis() - date.removeSuffix(" giây trước").toLong() * 1000
			date.contains("phút trước") -> System.currentTimeMillis() - date.removeSuffix(" phút trước")
				.toLong() * 60 * 1000

			date.contains("giờ trước") -> System.currentTimeMillis() - date.removeSuffix(" giờ trước")
				.toLong() * 60 * 60 * 1000

			date.contains("ngày trước") -> System.currentTimeMillis() - date.removeSuffix(" ngày trước")
				.toLong() * 24 * 60 * 60 * 1000

			date.contains("tuần trước") -> System.currentTimeMillis() - date.removeSuffix(" tuần trước")
				.toLong() * 7 * 24 * 60 * 60 * 1000

			date.contains("tháng trước") -> System.currentTimeMillis() - date.removeSuffix(" tháng trước")
				.toLong() * 30 * 24 * 60 * 60 * 1000

			date.contains("năm trước") -> System.currentTimeMillis() - date.removeSuffix(" năm trước")
				.toLong() * 365 * 24 * 60 * 60 * 1000

			else -> SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(date)?.time ?: 0L
		}
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/tim-kiem").parseHtml()
		return doc.select("label.ml-3.inline-flex.items-center.cursor-pointer.select-none").map { label ->
			val key = label.attr("@click").substringAfter("toggleGenre('").substringBefore("')").trim()
			// val key = getKeyTags(rawKey)
			val title = label.selectFirst("span.ml-2.text-sm.font-semibold.text-blueGray-600.text-ellipsis")?.text() ?: ""
			MangaTag(
				key = key,
				title = title,
				source = source,
			)
		}.toSet()
	}

	// Fix tags, if its necessary!
	// private fun getKeyTags(rawKey: String): String {
	// 	val number = rawKey.trim().replace(Regex("[^0-9]"), "")
	// 	return (number.toIntOrNull()?.plus(1) ?: 0).toString()
	// }
}
