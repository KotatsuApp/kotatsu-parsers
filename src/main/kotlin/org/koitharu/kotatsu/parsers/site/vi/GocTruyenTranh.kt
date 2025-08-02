package org.koitharu.kotatsu.parsers.site.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import java.util.*

@MangaSourceParser("GOCTRUYENTRANH", "Góc Truyện Tranh", "vi")
internal class GocTruyenTranh(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.GOCTRUYENTRANH, 30) {

	override val configKeyDomain = ConfigKey.Domain("goctruyentranh.org")

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
						tag.key
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

	private suspend fun availableTags(): Set<MangaTag> {
		val url = "https://$domain/baseapi/categories/getCategories"
		val response = webClient.httpGet(url).parseJson()
		val arr = response.getJSONArray("categories")
		return arr.mapJSONToSet { jo ->
			MangaTag(
				title = jo.getString("name"),
				key = jo.getLong("id").toString(),
				source = source,
			)
		}
	}
}
