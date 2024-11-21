package org.koitharu.kotatsu.parsers.site.heancms

import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.asTypedList
import org.koitharu.kotatsu.parsers.util.json.getFloatOrDefault
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.unescapeJson
import java.text.SimpleDateFormat
import java.util.*

internal abstract class HeanCms(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 20,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
		SortOrder.UPDATED,
		SortOrder.UPDATED_ASC,
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_ASC,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED, MangaState.ABANDONED),
	)

	protected open val pathManga = "series"
	protected open val apiPath
		get() = getDomain("api")

	protected open val paramsUpdated = "latest"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(apiPath)
			append("/query?query_string=")

			filter.query?.let {
				append(filter.query.urlEncoded())
			}

			append("&series_type=Comic&perPage=$pageSize")

			filter.states.oneOrThrowIfMany()?.let {
				append("&status=")
				append(
					when (it) {
						MangaState.ONGOING -> "Ongoing"
						MangaState.FINISHED -> "Completed"
						MangaState.ABANDONED -> "Dropped"
						MangaState.PAUSED -> "Hiatus"
						else -> ""
					},
				)
			}

			append("&orderBy=")
			when (order) {
				SortOrder.POPULARITY -> append("total_views&order=desc")
				SortOrder.POPULARITY_ASC -> append("total_views&order=asc")
				SortOrder.UPDATED -> append("$paramsUpdated&order=desc")
				SortOrder.UPDATED_ASC -> append("$paramsUpdated&order=asc")
				SortOrder.NEWEST -> append("created_at&order=desc")
				SortOrder.NEWEST_ASC -> append("created_at&order=asc")
				SortOrder.ALPHABETICAL -> append("title&order=asc")
				SortOrder.ALPHABETICAL_DESC -> append("title&order=desc")
				else -> append("latest&order=desc")
			}
			append("&tags_ids=")
			append("[".urlEncoded())
			append(filter.tags.joinToString(",") { it.key })
			append("]".urlEncoded())

			append("&page=")
			append(page.toString())
		}


		return parseMangaList(webClient.httpGet(url).parseJson())
	}

	protected open val cdn = "api.$domain/"

	private fun parseMangaList(response: JSONObject): List<Manga> {
		return response.getJSONArray("data").mapJSON { it ->
			val id = it.getLong("id")
			val url = "/comic/${it.getString("series_slug")}"
			val publicUrl = "/series/${it.getString("series_slug")}"
			val title = it.getString("title")
			val cover = if (it.getString("thumbnail").startsWith("https://")) {
				it.getString("thumbnail")
			} else {
				"https://$cdn${it.getString("thumbnail")}"
			}

			Manga(
				id = id,
				url = url,
				title = title,
				altTitle = it.getString("alternative_names").takeIf { it.isNotBlank() },
				publicUrl = publicUrl.toAbsoluteUrl(domain),
				description = it.getString("description"),
				rating = it.getFloatOrDefault("rating", RATING_UNKNOWN) / 5f,
				isNsfw = isNsfwSource,
				coverUrl = cover,
				tags = emptySet(),
				state = when (it.getString("status")) {
					"Ongoing" -> MangaState.ONGOING
					"Completed" -> MangaState.FINISHED
					"Dropped" -> MangaState.ABANDONED
					"Hiatus" -> MangaState.PAUSED
					else -> null
				},
				author = null,
				source = source,
			)
		}
	}

	protected open val datePattern = "yyyy-MM-dd"

	override suspend fun getDetails(manga: Manga): Manga {
		val seriesId = manga.id
		val url = "https://$apiPath/chapter/query?page=1&perPage=9999&series_id=$seriesId"
		val response = webClient.httpGet(url).parseJson()
		val data = response.getJSONArray("data").asTypedList<JSONObject>()
		val dateFormat = SimpleDateFormat(datePattern, Locale.ENGLISH)
		return manga.copy(
			chapters = data.mapChapters(reversed = true) { i, it ->
				val chapterUrl =
					"/series/${it.getJSONObject("series").getString("series_slug")}/${it.getString("chapter_slug")}"
				MangaChapter(
					id = it.getLong("id"),
					name = it.getString("chapter_name"),
					number = i + 1f,
					volume = 0,
					url = chapterUrl,
					scanlator = null,
					uploadDate = dateFormat.tryParse(it.getString("created_at").substringBefore("T")),
					branch = null,
					source = source,
				)
			},
		)
	}

	protected open val selectPages = ".flex > img:not([alt])"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPages).map { img ->
			val url = img.requireSrc()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/comics").parseHtml()
		val regex = Regex("\"tags\\\\?\":\\s*\\[(.+?)]\\s*[},]")
		val tags = doc.select("script").joinToString("") { it.html() }
			.let { fullHtml ->
				regex.find(fullHtml)?.groupValues?.getOrNull(1)
			}
			?.unescapeJson()
			?.replace(Regex(""""]\)\s*self\.__next_f\.push\(\[\d+,""""), "")
			?.let { "[$it]" }
			?: return emptySet()

		return JSONArray(tags).mapJSON {
			MangaTag(
				key = it.getInt("id").toString(),
				title = it.getString("name").toTitleCase(sourceLocale),
				source = source,
			)
		}.toSet()
	}

}
