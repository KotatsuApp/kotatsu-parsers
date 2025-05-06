package org.koitharu.kotatsu.parsers.site.vi

import org.json.JSONArray
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MIMIHENTAI", "MimiHentai", "vi", type = ContentType.HENTAI)
internal class MimiHentai(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.MIMIHENTAI, 18) {

	private val apiSuffix = "api/v1/manga"
	override val configKeyDomain = ConfigKey.Domain("mimihentai.com")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isMultipleTagsSupported = true,
			isAuthorSearchSupported = true,
		)

	init {
		setFirstPage(0)
	}

	override suspend fun getFilterOptions() = MangaListFilterOptions(availableTags = fetchTags())

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/$apiSuffix/advance-search?page=")
			append(page)
			append("&max=18") // page size, avoid rate limit
			when {
				!filter.query.isNullOrEmpty() -> {
					append("&name=")
					append(filter.query.urlEncoded())
				}

				!filter.author.isNullOrEmpty() -> {
					append("&author=")
					append(filter.author.urlEncoded())
				}

				filter.tags.isNotEmpty() -> {
					append("&genre=")
					append(filter.tags.joinToString(",") { it.key })
				}
			}
		}

		val json = webClient.httpGet(url).parseJson()
		val data = json.getJSONArray("data")
		return parseMangaList(data)
	}

	private fun parseMangaList(data: JSONArray): List<Manga> {
		return data.mapJSON { jo ->
			val id = jo.getLong("id")
			val title = jo.getString("title")
			val description = jo.getStringOrNull("description")
			val authors = jo.getJSONArray("authors").asTypedList<String>().toSet()
			val differentNames = jo.getJSONArray("differentNames").asTypedList<String>().toSet()
			val state = when (description) {
				"Đang Tiến Hành" -> MangaState.ONGOING
				"Hoàn Thành" -> MangaState.FINISHED
				else -> null
			}

			Manga(
				id = generateUid(id),
				title = title,
				altTitles = differentNames,
				url = "/$apiSuffix/info/$id",
				publicUrl = "https://$domain/g/$id",
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = jo.getString("coverUrl"),
				tags = emptySet(),
				state = state,
				authors = authors,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = manga.url.toAbsoluteUrl(domain)
		val json = webClient.httpGet(url).parseJson()

		val relationInfo = json.getJSONObject("relationInfo")
		val tags = relationInfo.getJSONArray("genres").mapJSONToSet { jo ->
			MangaTag(
				title = jo.getString("name").toTitleCase(sourceLocale),
				key = jo.getLong("id").toString(),
				source = source,
			)
		}

		val basicInfo = json.getJSONObject("basicInfo")
		val id = basicInfo.getLong("id")
		val description = basicInfo.getStringOrNull("description")
		val uploaderName = json.getStringOrNull("uploaderName")
		val urlChaps = "https://$domain/$apiSuffix/gallery/$id"
		val parsedChapters = webClient.httpGet(urlChaps).parseJsonArray()
		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US)
		val chapters = parsedChapters.mapJSON { jo ->
			MangaChapter(
				id = generateUid(jo.getLong("id")),
				title = jo.getStringOrNull("title"),
				number = jo.getFloatOrDefault("order", 0f),
				url = "/$apiSuffix/chapter?id=${jo.getLong("id")}",
				uploadDate = dateFormat.parse(jo.getString("createdAt"))?.time ?: 0L,
				source = source,
				scanlator = uploaderName,
				branch = null,
				volume = 0,
			)
		}.reversed()

		return manga.copy(
			tags = tags,
			description = description,
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val json = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseJson()
		val imageUrls = json.getJSONArray("pages").asTypedList<String>()
		return imageUrls.map { url ->
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val url = "https://$domain/$apiSuffix/genres"
		val response = webClient.httpGet(url).parseJsonArray()
		return response.mapJSONToSet { jo ->
			MangaTag(
				title = jo.getString("name").toTitleCase(sourceLocale),
				key = jo.getLong("id").toString(),
				source = source,
			)
		}
	}
}
