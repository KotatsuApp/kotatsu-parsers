package org.koitharu.kotatsu.parsers.site.uk

import androidx.collection.ArraySet
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getFloatOrDefault
import org.koitharu.kotatsu.parsers.util.json.getIntOrDefault
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.suspendlazy.getOrNull
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 20
private const val INFINITE = 999999
private const val HEADER_ENCODING = "Content-Encoding"
private const val IMAGE_BASEURL_FALLBACK = "https://hmvolumestorage.b-cdn.net/public-resources"

@MangaSourceParser("HONEYMANGA", "HoneyManga", "uk")
internal class HoneyMangaParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.HONEYMANGA, PAGE_SIZE),
	Interceptor {

	private val urlApi get() = "https://data.api.$domain"
	private val mangaApi get() = "$urlApi/v2/manga/cursor-list"
	private val chapterApi get() = "$urlApi/v2/chapter/cursor-list"
	private val genresListApi get() = "$urlApi/genres-tags/genres-list"
	private val framesApi get() = "$urlApi/chapter/frames"
	private val searchApi get() = "https://search.api.$domain/v2/manga/pattern?query="

	private val imageStorageUrl = suspendLazy(initializer = ::fetchCoversBaseUrl)

	override val configKeyDomain = ConfigKey.Domain("honey-manga.com.ua")

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
	)

	override suspend fun getDetails(manga: Manga): Manga {
		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
		val body = JSONObject()
		body.put("mangaId", manga.url)
		body.put("pageSize", INFINITE) // Hack lol (no)
		body.put("page", 1)
		body.put("sortOrder", "ASC")
		val chapterRequest = webClient.httpPost(chapterApi, body).parseJson()
		return manga.copy(
			chapters = chapterRequest.getJSONArray("data").mapJSON { jo ->
				val number = jo.getFloatOrDefault("chapterNum", 0f)
				val volume = jo.getIntOrDefault("volume", 0)
				MangaChapter(
					id = generateUid(jo.getString("id")),
					name = buildString {
						append("Том ")
						append(volume)
						append(". ")
						append("Розділ ")
						append(number)
						if (jo.optString("title") != "Title") {
							append(" - ")
							append(jo.optString("title"))
						}
					},
					number = number,
					volume = volume,
					url = jo.optString("chapterResourcesId"),
					scanlator = null,
					uploadDate = dateFormat.tryParse(jo.getString("lastUpdated")),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val body = JSONObject()
		body.put("page", page)
		body.put("pageSize", PAGE_SIZE)
		val sort = JSONObject()
		sort.put("sortBy", getSortKey(order))
		sort.put("sortOrder", "DESC")
		body.put("sort", sort)

		val content = when {
			filter.tags.isNotEmpty() -> {
				// Tags
				val filters = JSONArray()
				val tagFilter = JSONObject()
				tagFilter.put("filterBy", "genres")
				tagFilter.put("filterOperator", "ALL")
				val tag = JSONArray()
				filter.tags.forEach {
					tag.put(it.title)
				}
				tagFilter.put("filterValue", tag)
				filters.put(tagFilter)
				body.put("filters", filters)
				webClient.httpPost(mangaApi, body).parseJson().getJSONArray("data")

			}

			!filter.query.isNullOrEmpty() -> {
				// Search
				when {
					filter.query.length < 3 -> throw IllegalArgumentException(
						"The query must contain at least 3 characters (Запит має містити щонайменше 3 символи)",
					)

					page == searchPaginator.firstPage -> webClient
						.httpGet(searchApi + filter.query.urlEncoded())
						.parseJsonArray()

					else -> JSONArray()
				}
			}

			else -> {
				// Popular/Newest
				body.put("filters", JSONArray())
				webClient.httpPost(mangaApi, body).parseJson().getJSONArray("data")
			}
		}
		return content.mapJSON { jo ->
			val id = jo.getString("id")
			val posterUrl = jo.getString("posterUrl")
			Manga(
				id = generateUid(id),
				title = jo.getString("title"),
				altTitle = jo.getStringOrNull("alternativeTitle"),
				url = id,
				publicUrl = "https://$domain/book/$id",
				rating = RATING_UNKNOWN,
				isNsfw = isNsfw(jo.getStringOrNull("adult")),
				coverUrl = getCoverUrl(posterUrl, 256),
				tags = getTitleTags(jo.optJSONArray("genresAndTags")),
				state = when (jo.getStringOrNull("titleStatus")) {
					"Онгоінг" -> MangaState.ONGOING
					"Завершено" -> MangaState.FINISHED
					else -> null
				},
				author = null,
				largeCoverUrl = getCoverUrl(posterUrl, 1080),
				description = jo.getStringOrNull("description"),
				chapters = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val content = webClient.httpGet("$framesApi/${chapter.url}").parseJson().getJSONObject("resourceIds")
		val baseUrl = imageStorageUrl.getOrNull() ?: IMAGE_BASEURL_FALLBACK
		return List(content.length()) { i ->
			val item = content.getString(i.toString())
			MangaPage(id = generateUid(item), "$baseUrl/$item", getCoverUrl(item, 256), source)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		// https://data.api.honey-manga.com.ua/genres-tags/genres-list
		val content = webClient.httpGet(genresListApi).parseJsonArray()
		val tagsSet = ArraySet<MangaTag>(content.length())
		repeat(content.length()) { i ->
			val item = content.getString(i)
			tagsSet.add(MangaTag(item, item, source))
		}
		return tagsSet
	}

	// Need for disable encoding (with encoding not working)
	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val newRequest = if (request.header(HEADER_ENCODING) != null) {
			request.newBuilder().removeHeader(HEADER_ENCODING).build()
		} else {
			request
		}
		return chain.proceed(newRequest)
	}

	private fun isNsfw(adultValue: String?): Boolean {
		val intValue = adultValue?.removeSuffix('+')?.toIntOrNull()
		return intValue != null && intValue >= 18
	}

	private suspend fun getCoverUrl(id: String, w: Int): String {
		val baseUrl = imageStorageUrl.getOrNull() ?: IMAGE_BASEURL_FALLBACK
		return concatUrl(baseUrl, "$id?optimizer=image&width=$w&height=$w")
	}

	private fun getSortKey(order: SortOrder?) = when (order) {
		SortOrder.POPULARITY -> "likes"
		SortOrder.NEWEST -> "lastUpdated"
		else -> "likes"
	}

	private fun getTitleTags(jsonTags: JSONArray): Set<MangaTag> {
		val tagsSet = ArraySet<MangaTag>(jsonTags.length())
		repeat(jsonTags.length()) { i ->
			val item = jsonTags.getString(i)

			tagsSet.add(MangaTag(title = item.toTitleCase(sourceLocale), key = item, source = source))
		}
		return tagsSet
	}

	private suspend fun fetchCoversBaseUrl(): String {
		val scriptUrl = webClient.httpGet("https://$domain")
			.parseHtml()
			.select("script")
			.firstNotNullOf { it.attrOrNull("src")?.takeIf { x -> x.contains("_app-") } }
		val script = webClient.httpGet(scriptUrl).parseRaw()
		// "vg":"https://hmvolumestorage.b-cdn.net/public-resources"
		return Regex("\"vg\":\"([^\"]+)\"").find(script)?.groups?.get(1)?.value ?: error("Image baseUrl not found")
	}
}
