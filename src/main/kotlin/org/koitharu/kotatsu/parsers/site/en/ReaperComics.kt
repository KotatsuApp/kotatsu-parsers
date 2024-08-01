package org.koitharu.kotatsu.parsers.site.en

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

private const val MAX_RETRY_COUNT = 5

@MangaSourceParser("REAPERCOMICS", "ReaperComics", "en")
internal class ReaperComics(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.REAPERCOMICS, pageSize = 20) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.ALPHABETICAL, SortOrder.POPULARITY, SortOrder.NEWEST, SortOrder.ALPHABETICAL_DESC)

	override val configKeyDomain = ConfigKey.Domain("reaperscans.com")

	private val userAgentKey = ConfigKey.UserAgent(context.getDefaultUserAgent())

	private val baseHeaders: Headers
		get() = Headers.Builder().add("User-Agent", config[userAgentKey]).build()

	override val headers
		get() = getApiHeaders()

	private fun getApiHeaders(): Headers {
		val userCookie = context.cookieJar.getCookies(domain).find {
			it.name == "user"
		} ?: return baseHeaders
		val jo = JSONObject(userCookie.value.urlDecode())
		val accessToken = jo.getStringOrNull("access_token") ?: return baseHeaders
		return baseHeaders.newBuilder().add("authorization", "bearer $accessToken").build()
	}

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		if(page > 1) return emptyList()

		val url = buildString {
			append("https://")
			append("api.$domain")
			append("/query?page=$page&perPage=9999&series_type=Comic")
			when (filter) {
				is MangaListFilter.Search -> {
					append("&query_string=")
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {
					append("&orderBy=")
					val order = when (filter.sortOrder) {
						SortOrder.UPDATED -> "updated_at"
						SortOrder.POPULARITY -> "total_views"
						SortOrder.NEWEST -> "created_at"
						SortOrder.ALPHABETICAL -> "title"
						SortOrder.ALPHABETICAL_DESC -> "title"
						else -> "updated_at"
					}
					append(order)
					val sortOrder = if (filter.sortOrder == SortOrder.ALPHABETICAL_DESC) "desc" else "asc"
					append("&order=$sortOrder")

					filter.states.oneOrThrowIfMany()?.let {
						append("&status=")
						append(
							when (it) {
								MangaState.ONGOING -> "Ongoing"
								MangaState.FINISHED -> "Completed"
								MangaState.ABANDONED -> "Dropped"
								MangaState.PAUSED -> "Hiatus"
								else -> "All"
							},
						)
					}
					if (filter.tags.isNotEmpty()) {
						append("&tags_ids=")
						append(filter.tags.joinToString(separator = "%") { it.key })
					}
				}

				null -> {
					append("&orderBy=updated_at")
					append("&order=asc")
					append("&adult=true")
					append("&status=All")
				}
			}
		}
		return parseMangaList(webClient.httpGet(url).parseJson())
	}


	private fun parseMangaList(response: JSONObject): List<Manga> {
		return response.getJSONArray("data").mapJSON { it ->
			val id = it.getLong("id")
			val url = "/comic/${it.getString("series_slug")}"
			val title = it.getString("title")
			val thumbnailPath = it.getString("thumbnail")
			Manga(
				id = id,
				url = url,
				title = title,
				altTitle = it.getString("alternative_names").takeIf { it.isNotBlank() },
				publicUrl = url.toAbsoluteUrl(domain),
				description = it.getString("description"),
				rating = it.getFloatOrDefault("rating", RATING_UNKNOWN) / 5f,
				isNsfw = isNsfwSource,
				coverUrl = "https://media.reaperscans.com/file/4SRBHm//$thumbnailPath",
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


	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/comics").parseHtml()
		val scriptContent = doc.select("script").find {
			it.data().contains("tags")
		}?.data()

		if (scriptContent != null) {
			val jsonString = scriptContent.substringAfter("push(").substringBeforeLast(")")
			val jsonArray = JSONArray(jsonString)
			val childrenArray = jsonArray.getString(1)
			val tagsString = childrenArray.substringAfter("tags:[").substringBeforeLast("]")
			val tagObjects = tagsString.split("},{")

			return tagObjects.mapNotNullTo(mutableSetOf()) { tagString ->

				val id = tagString.substringAfter("\"id\":").substringBefore(",")
				val name = tagString.substringAfter("\"name\":\"").substringBefore("\"")
				if (id.isNotEmpty() && name.isNotEmpty()) {
					MangaTag(
						key = id,
						title = name.toTitleCase(sourceLocale),
						source = source
					)
				} else {
					null
				}
			}
		}
		return emptySet()
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}
	private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", sourceLocale)

	override suspend fun getDetails(manga: Manga): Manga {
		val seriesid = manga.id
		val url = "https://api.$domain/chapter/query?page=1&perPage=9999&series_id=$seriesid"
		val response = makeRequest(url)
		val data = response.getJSONArray("data")
		val chapters = data.mapJSONIndexed { index, it ->
			val chapterUrl = "/series/${it.getJSONObject("series").getString("series_slug")}/${it.getString("chapter_slug")}"
			MangaChapter(
				id = it.getLong("id"),
				name = it.getString("chapter_name"),
				number = data.length() - index,
				url = chapterUrl,
				scanlator = null,
				uploadDate = parseChapterDate(dateFormat, it.getString("created_at")),
				branch = null,
				source = source,
			)
		}
		return manga.copy(
			chapters = chapters
		)
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		return try {
			dateFormat.tryParse(date)
		} catch (e: Exception) {
			0L
		}
	}

	private val pageSelector = "div#content div.container img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select(pageSelector).map { img ->
			val url = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun makeRequest(url: String): JSONObject {
		var retryCount = 0
		val backoffDelay = 2000L // Initial delay (milliseconds)
		val request = Request.Builder().url(url).headers(headers).build()

		while (true) {
			try {
				val response = context.httpClient.newCall(request).execute().parseJson()
				return response

			} catch (e: Exception) {
				// Log or handle the exception as needed
				if (++retryCount <= MAX_RETRY_COUNT) {
					withContext(Dispatchers.Default) {
						delay(backoffDelay)
					}
				} else {
					throw e
				}
			}
		}
	}
}

