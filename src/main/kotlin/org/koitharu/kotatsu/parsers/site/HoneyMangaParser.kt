package org.koitharu.kotatsu.core.parser

import androidx.collection.ArraySet
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONIndexed
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseJsonArray
import org.koitharu.kotatsu.parsers.util.removeSuffix
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale


private const val PAGE_SIZE = 20

@MangaSourceParser("HONEYMANGA", "Honey Manga", "uk")
class HoneyMangaParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.HONEYMANGA, PAGE_SIZE), Interceptor {

	private val urlApi get() = "https://data.api.$domain"
	private val mangaApi get() = "$urlApi/v2/manga/cursor-list"
	private val chapterApi get() = "$urlApi/v2/chapter/cursor-list"
	private val genresListApi get() = "$urlApi/genres-tags/genres-list"
	private val framesApi get() = "$urlApi/chapter/frames"
	private val searchApi get() = "https://search.api.$domain/api/v1/title/search-matching?query="

	private val imageStorageUrl = "https://manga-storage.fra1.digitaloceanspaces.com/public-resources"

	override val headers
		get() = Headers.Builder()
			.add("User-Agent", "Mozilla/5.0 (Android 13; Mobile; rv:68.0) Gecko/68.0 Firefox/109.0")
			.build()

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("honey-manga.com.ua", null)

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
	)

	override suspend fun getDetails(manga: Manga): Manga {
		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
		val body = JSONObject()
		body.put("mangaId", manga.url)
		body.put("pageSize", 999999) // Hack lol (no)
		body.put("page", 1)
		body.put("sortOrder", "ASC")
		val chapterRequest = webClient.httpPost(chapterApi, body).parseJson()
		return manga.copy(
			chapters = chapterRequest.getJSONArray("data").mapJSONIndexed() { i, jo ->
				MangaChapter(
					id = generateUid(jo.getString("id")),
					name = buildString {
						append("Том ")
						append(jo.optString("volume", "0"))
						append(". ")
						append("Розділ ")
						append(jo.optString("chapterNum", "0"))
						if (jo.optString("title") != "Title") {
							append(" - ")
							append(jo.optString("title"))
						}
					},
					number = i + 1,
					url = jo.optString("chapterResourcesId"),
					scanlator = null,
					uploadDate = dateFormat.tryParse(jo.getString("lastUpdated")),
					branch = null,
					source = source
				)
			}
		)
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder
	): List<Manga> {
		val body = JSONObject()
		var content: JSONArray = JSONArray()

		// Popular/Newest
		content = if (query.isNullOrEmpty()) {
			body.put("page", page)
			body.put("pageSize", PAGE_SIZE)
			body.put("filters", JSONArray())
			val sort = JSONObject()
			sort.put("sortBy", getSortKey(sortOrder))
			sort.put("sortOrder", "DESC")
			body.put("sort", sort)
			webClient.httpPost(mangaApi, body).parseJson().getJSONArray("data")
		// Search
		} else {
			if((query.length < 3) || (page > 1)) return emptyList()
			webClient.httpGet(searchApi + query.urlEncoded()).parseJsonArray()
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
		val body = JSONObject()
		body.put("chapterId", chapter.url)

		val content = webClient.httpPost(framesApi, body).parseJson().getJSONObject("resourceIds")
		return List(content.length()) { i ->
			val item = content.get(i.toString()).toString()
			MangaPage(id = generateUid(item), "$imageStorageUrl/$item", getCoverUrl(item, 256), source)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		// https://data.api.honey-manga.com.ua/genres-tags/genres-list
		val tagsSet = mutableListOf<MangaTag>()
		val content = webClient.httpGet(genresListApi).parseJsonArray()
		(0 until content.length()).forEach { i ->
			val item = content.get(i).toString()

			tagsSet.add(MangaTag(item, item, source))
		}

		return tagsSet.toSet()
	}

	// Need for disable encoding (with encoding not working)
	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val newRequest = if (request.header("Content-Encoding") != null) {
			request.newBuilder().removeHeader("Content-Encoding").build()
		} else {
			request
		}
		return chain.proceed(newRequest)
	}

	private fun isNsfw(adultValue: String?): Boolean {
		val intValue = adultValue?.removeSuffix('+')?.toIntOrNull()
		return intValue != null && intValue >= 18
	}

	private fun getCoverUrl(id: String, w: Int): String {
		return "https://$domain/_next/image?url=https%3A%2F%2Fmanga-storage.fra1.digitaloceanspaces.com%2Fpublic-resources%2F$id&w=$w&q=75"
	}

	private fun getSortKey(order: SortOrder?) = when (order) {
		SortOrder.POPULARITY -> "likes"
		SortOrder.NEWEST -> "lastUpdated"
		else -> "likes"
	}

	private fun getTitleTags(jsonTags: JSONArray): Set<MangaTag>  {
		val tagsSet = ArraySet<MangaTag>(jsonTags.length())
		(0 until jsonTags.length()).forEach { i ->
			val item = jsonTags.get(i)

			tagsSet.add(MangaTag(item.toString(), item.toString(), source))
		}

		return tagsSet.toSet()
	}
}
