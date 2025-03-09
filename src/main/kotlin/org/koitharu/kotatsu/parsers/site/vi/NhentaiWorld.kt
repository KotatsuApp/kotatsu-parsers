package org.koitharu.kotatsu.parsers.site.vi

import okhttp3.Headers
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import java.text.SimpleDateFormat
import java.util.*

@Broken // TODO
@MangaSourceParser("NHENTAIWORLD", "Nhentai World", "vi", ContentType.HENTAI)
internal class NhentaiWorld(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.NHENTAIWORLD, 24) {

	override val configKeyDomain = ConfigKey.Domain("nhentaiworld-h1.info")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("origin", "https://$domain")
		.add("referer", "https://$domain")
		.build()

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isMultipleTagsSupported = false,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("/genre/")
			if (filter.tags.isEmpty()) {
				append("all")
			} else {
				append(filter.tags.first().key)
			}

			append("?sort=")
			append(
				when (order) {
					SortOrder.UPDATED -> "recent-update"
					SortOrder.POPULARITY -> "view"
					else -> "recent-update"
				},
			)

			if (!filter.query.isNullOrEmpty()) {
				append("&search=")
				append(filter.query.urlEncoded())
			}

			if (filter.states.isNotEmpty()) {
				append("&status=")
				append(
					when (filter.states.first()) {
						MangaState.ONGOING -> "progress"
						MangaState.FINISHED -> "completed"
						else -> ""
					},
				)
			}

			append("&page=")
			append(page)
		}

		val doc = webClient.httpGet(url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("div.relative.mb-1.h-full.max-h-\\[375px\\]").map { div ->
			val img = div.selectFirst("img.hover\\:scale-105.transition-all.w-full.h-full")
			val a = div.selectFirstOrThrow("a")

			val title = img?.attr("alt").orEmpty()
			val coverUrl = img?.attrAsAbsoluteUrlOrNull("src")
			val href = a.attrAsRelativeUrl("href")

			Manga(
				id = generateUid(url),
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
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.selectFirst("div.flex-1.bg-neutral-900") ?: return manga
		val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
			timeZone = TimeZone.getTimeZone("GMT+7")
		}

		val tags = root.select("div.flex.flex-wrap.gap-2 button").mapNotNullToSet { button ->
			val tagName = button.text().toTitleCase(sourceLocale)
			val tagUrl = button.parent()?.attrOrNull("href")?.substringAfterLast('/')
			if (tagUrl != null) {
				MangaTag(title = tagName, key = tagUrl, source = source)
			} else {
				null
			}
		}

		val state = when {
			root.selectFirst("a[href*='status=completed']") != null -> MangaState.FINISHED
			root.selectFirst("a[href*='status=progress']") != null -> MangaState.ONGOING
			else -> null
		}

		val description = root.selectFirst("div#introduction-wrap p.font-light")?.html()?.nullIfEmpty()

		val altTitles = description?.split("\n")?.mapNotNullToSet { line ->
			when {
				line.startsWith("Tên tiếng anh:", ignoreCase = true) ->
					line.substringAfter(':').substringBefore("Tên gốc:").trim()

				line.startsWith("Tên gốc:", ignoreCase = true) ->
					line.substringAfter(':').trim().substringBefore(' ')

				else -> null
			}
		}

		val scriptTag = doc.select("script").firstOrNull { it.data().contains("\"data\":") }?.data()

		val chapters = ArrayList<MangaChapter>()
		if (!scriptTag.isNullOrEmpty()) {
			val jsonData = JSONObject(scriptTag)

			val viChaptersArray: JSONArray = jsonData.optJSONArray("data") ?: JSONArray()
			val enChaptersArray: JSONArray = jsonData.optJSONArray("chapterListEn") ?: JSONArray()

			listOf(
				Pair("Tiếng Việt", viChaptersArray),
				Pair("English", enChaptersArray),
			).flatMapTo(chapters) { (branch: String, chaptersArray: JSONArray) ->
				List(chaptersArray.length()) { i ->
					val chapterObj = chaptersArray.getJSONObject(i)
					val chapterName = chapterObj.getStringOrNull("name")
					val uploadDateStr = chapterObj.getStringOrNull("createdAt")
					val uploadDate = chapterDateFormat.tryParse(uploadDateStr)

					if (!chapterName.isNullOrEmpty()) {
						MangaChapter(
							id = generateUid("${manga.url}/$chapterName?lang=${if (branch == "Tiếng Việt") "VI" else "EN"}"),
							title = chapterName,
							number = chapterName.toFloatOrNull() ?: (i + 1).toFloat(),
							url = "/read/${manga.id}/$chapterName?lang=${if (branch == "Tiếng Việt") "VI" else "EN"}",
							scanlator = null,
							uploadDate = uploadDate,
							branch = branch,
							source = source,
							volume = 0,
						)
					} else null
				}.filterNotNull()
			}
		}

		return manga.copy(
			tags = tags,
			state = state,
			description = description,
			altTitles = altTitles.orEmpty(),
			chapters = chapters,
		)
	}


	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("img.m-auto.read-image.w-auto.h-auto.md\\:min-h-\\[800px\\].min-h-\\[300px\\]")
			.map { img ->
				val url = img.requireSrc()
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val doc = webClient.httpGet("$domain/genre/all").parseHtml()
		val tagItems = doc.select("div.genre-list a")
		return tagItems.mapNotNullToSet { item ->
			val title = item.text().toTitleCase(sourceLocale)
			val key = item.attr("href").substringAfterLast('/')
			if (key.isNotEmpty() && title.isNotEmpty()) {
				MangaTag(title = title, key = key, source = source)
			} else {
				null
			}
		}
	}
}

