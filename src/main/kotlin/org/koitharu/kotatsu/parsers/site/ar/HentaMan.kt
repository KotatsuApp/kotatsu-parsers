package org.koitharu.kotatsu.parsers.site.ar

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONIndexed
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import org.koitharu.kotatsu.parsers.util.json.toJSONArrayOrNull
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseSafe
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet

@MangaSourceParser("HENTAMAN", "Hentaman", "ar", ContentType.HENTAI)
internal class HentaMan(context: MangaLoaderContext) : PagedMangaParser(
	context,
	source = MangaParserSource.HENTAMAN,
	pageSize = 12,
) {
	override val configKeyDomain = ConfigKey.Domain("hentaman.net")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_TODAY,
		SortOrder.POPULARITY_WEEK,
		SortOrder.RATING,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	private val dateFormat by lazy {
		SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", sourceLocale)
	}

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		val tags = fetchAvailableTags()
		return MangaListFilterOptions(
			availableTags = tags,
			availableStates = EnumSet.of(
				MangaState.ONGOING,
				MangaState.FINISHED,
				MangaState.PAUSED,
				MangaState.ABANDONED,
			),
			availableContentTypes = EnumSet.of(
				ContentType.MANGA,
				ContentType.MANHWA,
			),
		)
	}

	private val baseUrl get() = "https://$domain"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {

		val url = when {
			!filter.query.isNullOrEmpty() -> {
				val searchUrl = "$baseUrl/manga/search".toHttpUrl().newBuilder()
					.addQueryParameter("query", filter.query)
					.build()
				val response = webClient.httpGet(searchUrl).parseJson()
				val results = response.optJSONArray("results") ?: return emptyList()
				return results.mapJSON { parseMangaFromJson(it) }
			}

			filter.tags.isNotEmpty() -> {
				val tag = filter.tags.first()
				"$baseUrl/list/genre/${tag.key}".toHttpUrl().newBuilder()
					.addQueryParameter("page", page.toString())
					.build()
			}

			filter.states.isNotEmpty() -> {
				val state = when (filter.states.first()) {
					MangaState.ONGOING -> "مستمر"
					MangaState.FINISHED -> "مكتمل"
					MangaState.PAUSED -> "متوقف"
					MangaState.ABANDONED -> "ملغى"
					else -> "مستمر"
				}
				"$baseUrl/list/status/$state".toHttpUrl().newBuilder()
					.addQueryParameter("page", page.toString())
					.build()
			}

			filter.types.isNotEmpty() -> {
				val type = when (filter.types.first()) {
					ContentType.MANGA -> "مانجا"
					ContentType.MANHWA -> "مانهوا"
					else -> "مانجا"
				}
				"$baseUrl/list/type/$type".toHttpUrl().newBuilder()
					.addQueryParameter("page", page.toString())
					.build()
			}

			else -> {
				when (order) {
					SortOrder.POPULARITY -> "$baseUrl/list/top/total_views"
					SortOrder.POPULARITY_TODAY -> "$baseUrl/list/top/today"
					SortOrder.POPULARITY_WEEK -> "$baseUrl/list/top/week"
					SortOrder.RATING -> "$baseUrl/list/top/all-time"
					else -> "$baseUrl/list/status/completed"
				}.toHttpUrl().newBuilder()
					.addQueryParameter("page", page.toString())
					.build()
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return parseListFromDocument(doc)
	}

	private fun parseListFromDocument(doc: Document): List<Manga> {
		val dataPage = doc.selectFirst("div#app")?.attr("data-page") ?: return emptyList()
		val pageData = JSONObject(dataPage)
		val props = pageData.optJSONObject("props") ?: return emptyList()

		val mangasData = when {
			props.has("data") -> {
				val data = props.getJSONObject("data")
				data.optJSONObject("mangas") ?: data
			}

			props.has("mangas") -> props.getJSONObject("mangas")
			else -> return emptyList()
		}

		val mangasArray = mangasData.optJSONArray("data") ?: return emptyList()
		return mangasArray.mapJSON { parseMangaFromJson(it) }
	}

	private fun parseMangaFromJson(json: JSONObject): Manga {
		val title = json.getString("title")
		val slug = json.getString("slug").urlEncoded()
		val cover = json.getString("cover")
		val url = "/manga/$slug"

		val rating = json.optString("average_rating").toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN

		return Manga(
			id = generateUid(url),
			title = title,
			altTitles = emptySet(),
			url = url,
			publicUrl = "$baseUrl$url",
			rating = rating,
			contentRating = ContentRating.ADULT,
			coverUrl = "$baseUrl/storage/covers/md/$cover",
			largeCoverUrl = "$baseUrl/storage/covers/lg/$cover",
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			description = null,
			chapters = null,
			source = source,
		)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.publicUrl.toHttpUrl()).parseHtml()
		val dataPage = doc.selectFirst("div#app")?.attr("data-page") ?: return manga

		val pageData = JSONObject(dataPage)
		val props = pageData.getJSONObject("props")
		val mangaData = props.getJSONObject("manga")

		val description = mangaData.optString("description")
		val author = mangaData.optString("author")
		val status = mangaData.optString("status")
		val rating = mangaData.optString("average_rating").toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN

		val state = when (status) {
			"ongoing", "مستمر" -> MangaState.ONGOING
			"completed", "مكتمل" -> MangaState.FINISHED
			"paused", "متوقف" -> MangaState.PAUSED
			"cancelled", "ملغى" -> MangaState.ABANDONED
			else -> null
		}

		val genres = mangaData.optJSONArray("genres")?.mapJSON { genreJson ->
			MangaTag(
				key = genreJson.getString("slug"),
				title = genreJson.getString("title"),
				source = source,
			)
		}?.toSet() ?: emptySet()

		val chapters =
			props.getJSONObject("chapters").optJSONArray("data")?.mapJSONNotNull { chapterJson ->
				parseChapter(chapterJson, manga.url)
			} ?: emptyList()

		return manga.copy(
			title = mangaData.getString("title"),
			description = description,
			state = state,
			tags = genres,
			chapters = chapters,
			authors = setOfNotNull(author.takeIf { !it.isNullOrEmpty() && it != "null" }),
			rating = rating,
			contentRating = ContentRating.ADULT,
		)
	}

	private fun parseChapter(json: JSONObject, mangaUrl: String): MangaChapter? {
		// Skip locked chapters
		val shortLink = json.optString("short_link")
		if (!shortLink.isNullOrEmpty() && shortLink != "null") {
			return null
		}

		val id = json.getLong("id")
		val chapterNumberStr = json.getString("chapter_number")
		val chapterNumber = chapterNumberStr.toFloatOrNull() ?: 0f
		val chapterName = json.optString("chapter_name").takeIf { it.isNotBlank() && it != "null" }
		val createdAt = json.getString("created_at")

		val title = chapterName ?: "الفصل $chapterNumberStr"

		return MangaChapter(
			id = generateUid(id),
			title = title,
			number = chapterNumber,
			volume = 0,
			url = "$mangaUrl/$chapterNumberStr",
			scanlator = null,
			uploadDate = dateFormat.parseSafe(createdAt),
			branch = null,
			source = source,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = "$baseUrl${chapter.url}"
		val doc = webClient.httpGet(chapterUrl).parseHtml()
		val dataPage = doc.selectFirst("div#app")?.attr("data-page") ?: return emptyList()

		val pageData = JSONObject(dataPage)
		val component = pageData.getString("component")
		// Skip locked chapters
		if (component == "LockedChapter") {
			return emptyList()
		}

		val props = pageData.getJSONObject("props")
		val data = props.getJSONObject("data")
		val manga = data.getJSONObject("manga")
		val currentChapter = data.getJSONObject("current_chapter")

		val imagesJsonString = currentChapter.getString("images")
		val chapterDir = currentChapter.getString("dir")
		val mangaDirectory = manga.getString("directory")

		val imagesArray = imagesJsonString.toJSONArrayOrNull() ?: return emptyList()

		return imagesArray.mapJSONIndexed { index, imageJson ->
			val imageName = imageJson.getString("name")
			val imageUrl = "$baseUrl/storage/mangas/low/$mangaDirectory/$chapterDir/$imageName"

			MangaPage(
				id = generateUid("${chapter.id}_$index"),
				url = imageUrl,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("$baseUrl/genres".toHttpUrl()).parseHtml()
		val dataPage = doc.selectFirst("div#app")?.attr("data-page") ?: return emptySet()
		val pageData = JSONObject(dataPage)
		val genres = pageData.getJSONObject("props").optJSONArray("genres") ?: return emptySet()

		return genres.mapJSON { genreJson ->
			MangaTag(
				key = genreJson.getString("slug"),
				title = genreJson.getString("title"),
				source = source,
			)
		}.toSet()
	}
}
