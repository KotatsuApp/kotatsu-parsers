package org.koitharu.kotatsu.parsers.site.en

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.*
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.asTypedList
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

@Broken
@MangaSourceParser("FLIXSCANSORG", "FlixScans.org", "en")
internal class FlixScansOrg(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.FLIXSCANSORG, 18) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)
	override val configKeyDomain = ConfigKey.Domain("flixscans.org")

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.allOf(MangaState::class.java),
		availableContentRating = EnumSet.of(ContentRating.ADULT),
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val json = when {
			!filter.query.isNullOrEmpty() -> {
				throw IllegalArgumentException(ErrorMessages.SEARCH_NOT_SUPPORTED)
			}

			else -> {

				val url = buildString {
					append("https://api.")
					append(domain)
					append("/api/v1/search/advance?=&serie_type=webtoon&page=")
					append(page.toString())

					append("&genres=")
					append(filter.tags.joinToString(separator = ",") { it.key })

					filter.states.oneOrThrowIfMany()?.let {
						append("&status=")
						append(
							when (it) {
								MangaState.ONGOING -> "ongoing"
								MangaState.FINISHED -> "completed"
								MangaState.ABANDONED -> "droped"
								MangaState.PAUSED -> "onhold"
								MangaState.UPCOMING -> "soon"
							},
						)
					}

					filter.contentRating.oneOrThrowIfMany()?.let {
						append("&adult=")
						append(
							when (it) {
								ContentRating.ADULT -> "true"
								else -> ""
							},
						)
					}
					append("&serie_type=webtoon")
				}
				webClient.httpGet(url).parseJson().getJSONArray("data")
			}
		}
		return json.mapJSON { j ->
			val href = "https://$domain/series/${j.getString("prefix")}-${j.getString("id")}-${j.getString("slug")}"
			val cover = "https://media.$domain/" + j.getString("thumbnail")
			Manga(
				id = generateUid(href),
				title = j.getString("title"),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = isNsfwSource,
				coverUrl = cover,
				tags = emptySet(),
				state = when (j.getString("status")) {
					"ongoing" -> MangaState.ONGOING
					"completed" -> MangaState.FINISHED
					"onhold" -> MangaState.PAUSED
					"droped" -> MangaState.ABANDONED
					else -> null
				},
				author = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/search/advance").parseHtml()
		val json = JSONArray(doc.requireElementById("__NUXT_DATA__").data())
		val tagsList = json.getJSONArray(3).toString().replace("[", "").replace("]", "").split(",")
		return tagsList.mapToSet { idTag ->
			val id = idTag.toInt()
			val idKey = json.getJSONObject(id).getInt("id")
			val key = json.getInt(idKey).toString()
			val idName = json.getJSONObject(id).getInt("name")
			val name = json.getString(idName)
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val chaptersDeferred = async { loadChapters(manga.url) }
		val json = JSONArray(doc.requireElementById("__NUXT_DATA__").data())
		val descId = json.getJSONObject(6).getInt("story")
		val desc = json.getString(descId)
		val tagsId = json.getJSONObject(6).getInt("genres")
		val tagsList = json.getJSONArray(tagsId).toString().replace("[", "").replace("]", "").split(",")
		val ratingId = json.getJSONObject(6).getInt("rating")
		val rating = json.getString(ratingId)
		val nsfwId = json.getJSONObject(6).getInt("nsfw")
		val nsfw = json.getBoolean(nsfwId)
		manga.copy(
			description = desc,
			tags = tagsList.mapToSet { idTag ->
				val id = idTag.toInt()
				val idKey = json.getJSONObject(id).getInt("id")
				val key = json.get(idKey).toString()
				val idName = json.getJSONObject(id).getInt("name")
				val name = json.get(idName).toString()
				MangaTag(
					key = key,
					title = name,
					source = source,
				)
			},
			rating = rating?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
			isNsfw = nsfw,
			chapters = chaptersDeferred.await(),
		)
	}

	private val dateFormat = SimpleDateFormat("yyyy-MM-dd", sourceLocale)

	private suspend fun loadChapters(baseUrl: String): List<MangaChapter> {
		val key = baseUrl.substringAfter("-").substringBefore("-")
		val seriesKey = baseUrl.substringAfterLast("/").substringBefore("-")
		val json = JSONArray(
			webClient.httpGet("https://api.$domain/api/v1/webtoon/chapters/$key-desc").parseRaw(),
		).asTypedList<JSONObject>().reversed()
		return json.mapChapters { i, j ->
			val url = "https://$domain/read/webtoon/$seriesKey-${j.getString("id")}-${j.getString("slug")}"
			val date = j.getString("createdAt").substringBeforeLast("T")
			MangaChapter(
				id = generateUid(url),
				url = url,
				name = j.getString("slug").replace('-', ' '),
				number = i + 1f,
				volume = 0,
				branch = null,
				uploadDate = dateFormat.tryParse(date),
				scanlator = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val json = JSONArray(doc.requireElementById("__NUXT_DATA__").data())
		val chapterData = json.getJSONObject(6).getInt("chapterData")
		val pageLocate = json.getJSONObject(chapterData).getInt("webtoon")
		val jsonPages = json.getJSONArray(pageLocate)
		val pages = ArrayList<MangaPage>(jsonPages.length())
		for (i in 0 until jsonPages.length()) {
			val id = jsonPages.getInt(i)
			val url = "https://media.$domain/" + json.getString(id)
			pages.add(
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}
}
