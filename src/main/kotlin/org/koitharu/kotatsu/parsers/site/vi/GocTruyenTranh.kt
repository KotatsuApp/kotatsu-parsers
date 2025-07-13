package org.koitharu.kotatsu.parsers.site.vi

import androidx.collection.arraySetOf
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import java.util.*

@MangaSourceParser("GOCTRUYENTRANH", "Góc Truyện Tranh", "vi")
internal class GocTruyenTranh(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.GOCTRUYENTRANH, 30) {

	override val configKeyDomain = ConfigKey.Domain("goctruyentranh.net")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
		SortOrder.ALPHABETICAL,
		SortOrder.RATING,
		SortOrder.POPULARITY,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = false,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = availableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.OTHER,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/baseapi/comics/filterComic")
			append("?keyword=")
			append(filter.query?.urlEncoded() ?: "")

			if (filter.tags.isNotEmpty()) {
				append("&categories=")
				append(
					filter.tags.joinToString(",") { tag ->
						availableTags().find { it.title == tag.title }?.key ?: tag.key
					},
				)
			}

			append("&status=")
			when {
				filter.states.isEmpty() -> append("")
				filter.states.size > 1 -> append("")
				else -> append(
					when (filter.states.first()) {
						MangaState.ONGOING -> "0"
						MangaState.FINISHED -> "1"
						else -> ""
					},
				)
			}

			append("&sort=")
			append(
				when (order) {
					SortOrder.UPDATED -> "recently_updated"
					SortOrder.NEWEST -> "latest"
					SortOrder.NEWEST_ASC -> "oldest"
					SortOrder.RATING -> "rating"
					SortOrder.ALPHABETICAL -> "alphabet"
					SortOrder.POPULARITY -> "mostView"
					else -> "recently_updated"
				},
			)

			if (filter.types.isNotEmpty()) {
				append("&country=")
				append(
					filter.types.joinToString(",") {
						when (it) {
							ContentType.MANGA -> "manga"
							ContentType.MANHWA -> "manhwa"
							ContentType.MANHUA -> "manhua"
							ContentType.OTHER -> "other"
							else -> "manga"
						}
					},
				)
			}

			append("&page=")
			append(page)
		}

		val json = webClient.httpGet(url).parseJson()
		val data = json.getJSONObject("comics").getJSONArray("data")

		return List(data.length()) { i ->
			val item = data.getJSONObject(i)
			val slug = item.getString("slug")
			val mangaUrl = buildString {
				append("https://")
				append(domain)
				append("/")
				append(slug)
			}

			val categories = item.optJSONArray("categories")
			val tags = if (categories != null) {
				List(categories.length()) { j ->
					val category = categories.getJSONObject(j)
					MangaTag(
						key = category.getString("id"),
						title = category.getString("name").toTitleCase(sourceLocale),
						source = source,
					)
				}.toSet()
			} else {
				emptySet()
			}

			// Check NSFW manga by tags, API / Site not have this information
			val checkNsfw = tags.any { tag ->
				tag.key in setOf("25", "39", "41", "43", "57", "63")
			}

			Manga(
				id = generateUid(mangaUrl),
				url = "/$slug",
				publicUrl = mangaUrl,
				title = item.getString("name"),
				altTitles = setOfNotNull(item.getStringOrNull("origin_name")?.takeUnless { it == "null" }),
				description = item.getStringOrNull("content"),
				rating = RATING_UNKNOWN,
				contentRating = if (checkNsfw || isNsfwSource) ContentRating.ADULT else null,
				coverUrl = item.getStringOrNull("thumbnail"),
				tags = tags,
				state = when (item.optString("status")) {
					"0" -> MangaState.ONGOING
					"1" -> MangaState.FINISHED
					else -> null
				},
				source = source,
				authors = emptySet(),
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		return manga.copy(
			rating = doc.selectFirst("div > span.leading-none")?.text()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
			authors = setOfNotNull(doc.selectFirst("aside p:contains(Tác giả:) a[href^='/tac-gia/']")?.textOrNull()),
			chapters = doc.select("ul[itemtype='https://schema.org/ItemList'] li")
				.mapChapters(reversed = true) { i, li ->
					val a = li.selectFirstOrThrow("a")
					val href = a.attrAsRelativeUrl("href")
					val name = li.selectFirst("div.w-\\[50\\%\\].truncate.flex")?.text() ?: ""
					val dateText = li.selectFirst("div.w-\\[50\\%\\].truncate.text-center")?.text()
					MangaChapter(
						id = generateUid(href),
						title = name,
						number = i + 1f,
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = parseChapterDate(dateText),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("img.lozad.mx-auto.transition-all.max-w-full.relative").map { img ->
			val url = img.attr("data-src")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private fun parseChapterDate(dateText: String?): Long {
		if (dateText == null) return 0

		val number = dateText.filter { it.isDigit() }.toIntOrNull() ?: return 0
		val now = System.currentTimeMillis()

		return when {
			dateText.contains("phút trước") -> {
				now - (number * 60 * 1000L)
			}

			dateText.contains("giờ trước") -> {
				now - (number * 60 * 60 * 1000L)
			}

			dateText.contains("ngày trước") -> {
				now - (number * 24 * 60 * 60 * 1000L)
			}

			else -> 0L
		}
	}

	private fun availableTags() = arraySetOf(
		MangaTag("Action", "1", source),
		MangaTag("Adventure", "2", source),
		MangaTag("Fantasy", "3", source),
		MangaTag("Manhua", "4", source),
		MangaTag("Chuyển Sinh", "5", source),
		MangaTag("Truyện Màu", "6", source),
		MangaTag("Xuyên Không", "7", source),
		MangaTag("Manhwa", "8", source),
		MangaTag("Drama", "9", source),
		MangaTag("Historical", "10", source),
		MangaTag("Manga", "11", source),
		MangaTag("Seinen", "12", source),
		MangaTag("Comedy", "13", source),
		MangaTag("Martial Arts", "14", source),
		MangaTag("Mystery", "15", source),
		MangaTag("Romance", "16", source),
		MangaTag("Shounen", "17", source),
		MangaTag("Sports", "18", source),
		MangaTag("Supernatural", "19", source),
		MangaTag("Harem", "20", source),
		MangaTag("Webtoon", "21", source),
		MangaTag("School Life", "22", source),
		MangaTag("Psychological", "23", source),
		MangaTag("Cổ Đại", "24", source),
		MangaTag("Ecchi", "25", source),
		MangaTag("Gender Bender", "26", source),
		MangaTag("Shoujo", "27", source),
		MangaTag("Slice of Life", "28", source),
		MangaTag("Ngôn Tình", "29", source),
		MangaTag("Horror", "30", source),
		MangaTag("Sci-fi", "31", source),
		MangaTag("Tragedy", "32", source),
		MangaTag("Mecha", "33", source),
		MangaTag("Comic", "34", source),
		MangaTag("One shot", "35", source),
		MangaTag("Shoujo Ai", "36", source),
		MangaTag("Anime", "37", source),
		MangaTag("Josei", "38", source),
		MangaTag("Smut", "39", source),
		MangaTag("Shounen Ai", "40", source),
		MangaTag("Mature", "41", source),
		MangaTag("Soft Yuri", "42", source),
		MangaTag("Adult", "43", source),
		MangaTag("Doujinshi", "44", source),
		MangaTag("Live action", "45", source),
		MangaTag("Trinh Thám", "46", source),
		MangaTag("Việt Nam", "47", source),
		MangaTag("Truyện scan", "48", source),
		MangaTag("Cooking", "49", source),
		MangaTag("Tạp chí truyện tranh", "50", source),
		MangaTag("16+", "51", source),
		MangaTag("Thiếu Nhi", "52", source),
		MangaTag("Soft Yaoi", "53", source),
		MangaTag("Đam Mỹ", "54", source),
		MangaTag("BoyLove", "55", source),
		MangaTag("Yaoi", "56", source),
		MangaTag("18+", "57", source),
		MangaTag("Người Thú", "58", source),
		MangaTag("ABO", "59", source),
		MangaTag("Mafia", "60", source),
		MangaTag("Isekai", "61", source),
		MangaTag("Hệ Thống", "62", source),
		MangaTag("NTR", "63", source),
		MangaTag("Yuri", "64", source),
		MangaTag("Girl Love", "65", source),
		MangaTag("Demons", "66", source),
		MangaTag("Huyền Huyễn", "67", source),
		MangaTag("Detective", "68", source),
		MangaTag("Trọng Sinh", "69", source),
		MangaTag("Magic", "70", source),
	)
}
