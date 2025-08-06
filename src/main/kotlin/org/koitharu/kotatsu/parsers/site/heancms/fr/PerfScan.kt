package org.koitharu.kotatsu.parsers.site.heancms.fr

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONArray
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.heancms.HeanCms
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONIndexed
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseSafe
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("PERF_SCAN", "PerfScan", "fr")
internal class PerfScan(context: MangaLoaderContext) :
	HeanCms(context, MangaParserSource.PERF_SCAN, "perf-scan.xyz"), Interceptor {

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = fetchStatusMap().keys,
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.NOVEL,
		),
	)

	private var statusMap: Map<MangaState, String>? = null

	private val apiHeaders = Headers.headersOf(
		"Origin", "https://$domain",
		"Referer", "https://$domain/",
		"Cookie", "NEXT_LOCALE=$sourceLocale",
	)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override val cdn = "https://$apiPath/cdn/"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://$apiPath/series?page=$page&take=$pageSize&type=COMIC")

			filter.query?.let { append("&title=").append(it.urlEncoded()) }

			if (filter.states.isNotEmpty()) {
				val statusMapping = fetchStatusMap()
				filter.states.forEach { state ->
					statusMapping[state]?.let { statusId ->
						append("&statusIds[]=").append(statusId)
					}
				}
			}

			filter.tags.forEach { tag ->
				append("&genreIds[]=").append(tag.key.urlEncoded())
			}
		}

		val response = webClient.httpGet(url).parseJson()
		val data = response.optJSONArray("data")
		return parseMangaList(data)
	}

	private fun parseMangaList(jsonArray: JSONArray): List<Manga> {
		return jsonArray.mapJSON { mangaObject ->
			val id = mangaObject.getString("id")
			val slug = mangaObject.getString("slug")

			val authors = listOfNotNull(
				mangaObject.optString("author").takeIf { it.isNotBlank() && it != "null" },
				mangaObject.optString("artist").takeIf { it.isNotBlank() && it != "null" },
			).joinToString(" & ")

			Manga(
				id = generateUid(id),
				url = id,
				title = mangaObject.getString("title"),
				publicUrl = "/series/$slug".toAbsoluteUrl(domain),
				coverUrl = cdn + mangaObject.getString("thumbnail"),
				description = mangaObject.optString("description"),
				authors = setOf(authors).filter { it.isNotBlank() }.toSet(),
				tags = mangaObject.optJSONArray("SeriesGenre")?.mapJSONToSet {
					val genre = it.getJSONObject("Genre")
					MangaTag(genre.getString("name").toTitleCase(sourceLocale), genre.getString("id"), source)
				} ?: emptySet(),
				state = mangaObject.optJSONObject("Status")?.let { parseState(it.getString("name")) },
				contentRating = if (mangaObject.optBoolean("isAdult")) ContentRating.ADULT else ContentRating.SAFE,
				source = source,
				altTitles = setOf(),
				rating = RATING_UNKNOWN,
				largeCoverUrl = null,
				chapters = null,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val seriesId = manga.url
		val url = "https://$apiPath/series/$seriesId"
		val response = webClient.httpGet(url).parseJson()
		val seriesData = response.getJSONObject("data")

		val chapters = seriesData.optJSONArray("Chapter")?.mapJSON { chapterObj ->
			val chapterId = chapterObj.getString("id")
			val chapterNumber = chapterObj.getInt("index")

			MangaChapter(
				id = generateUid(chapterId),
				url = "/series/${seriesData.getString("slug")}/chapter/$chapterNumber",
				title = chapterObj.optString("title", "Chapitre $chapterNumber")
					.takeIf { it.isNotBlank() && it != "-" },
				number = chapterNumber.toFloat(),
				uploadDate = parseDate(chapterObj.getString("createdAt")),
				source = source,
				volume = 0,
				scanlator = null,
				branch = null,
			)
		}

		return manga.copy(chapters = chapters)
	}

	private fun parseDate(dateString: String): Long {
		return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parseSafe(dateString)
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		return page.url
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val newRequest = request.newBuilder()
			.headers(apiHeaders)
			.build()

		return chain.proceed(newRequest)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullApiUrl = "https://$apiPath${chapter.url}"
		val response = webClient.httpGet(fullApiUrl).parseJson()
		val data = response.getJSONObject("data")
		val chapterData = data.getJSONArray("content")

		return chapterData.mapJSONIndexed { i, pageObject ->
			val imageId = pageObject.getString("value")

			MangaPage(
				id = generateUid(imageId),
				url = cdn + imageId,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val url = "https://$apiPath/genre"
		val response = webClient.httpGet(url).parseJson()

		val genresArray = response.getJSONArray("data")
		return genresArray.mapJSONToSet { genreObject ->
			MangaTag(
				title = genreObject.getString("name").toTitleCase(sourceLocale),
				key = genreObject.getString("id"),
				source = source,
			)
		}
	}

	private fun parseState(status: String) = when (status) {
		"En cours" -> MangaState.ONGOING
		"Terminé" -> MangaState.FINISHED
		"Annulé" -> MangaState.ABANDONED
		"En pause" -> MangaState.PAUSED
		else -> null
	}

	private suspend fun fetchStatusMap(): Map<MangaState, String> {
		if (statusMap != null) return statusMap!!

		val url = "https://$apiPath/status"
		val response = webClient.httpGet(url).parseJson()

		val statusArray = response.getJSONArray("data")

		val map = mutableMapOf<MangaState, String>()
		statusArray.mapJSON { statusObject ->
			val name = statusObject.getString("name")
			val id = statusObject.getString("id")

			when (name) {
				"En cours" -> map[MangaState.ONGOING] = id
				"Terminé" -> map[MangaState.FINISHED] = id
				"Annulé" -> map[MangaState.ABANDONED] = id
				"En pause" -> map[MangaState.PAUSED] = id
			}
		}

		statusMap = map
		return map
	}
}
