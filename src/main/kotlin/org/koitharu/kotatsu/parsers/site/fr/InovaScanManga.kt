package org.koitharu.kotatsu.parsers.site.fr

import okhttp3.HttpUrl
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.SinglePageMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("INOVASCANMANGA", "InovaScanManga", "fr", type = ContentType.HENTAI)
internal class InovaScanManga(context: MangaLoaderContext) :
	SinglePageMangaParser(context, MangaParserSource.INOVASCANMANGA) {
	override val configKeyDomain = ConfigKey.Domain("inovascanmanga.com")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	private var genreCache: Set<MangaTag>? = null

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = fetchAvailableGenres(),
			availableStates = EnumSet.of(
				MangaState.ONGOING,
				MangaState.FINISHED,
				MangaState.PAUSED,
			),
			availableContentTypes = EnumSet.of(
				ContentType.MANGA,
				ContentType.MANHWA,
				ContentType.MANHUA,
			),
		)
	}

	private suspend fun fetchAvailableGenres(): Set<MangaTag> {
		genreCache?.let { return it }

		val url = buildApiUrl(
			search = "",
			sort = "trending",
			status = "all",
			genre = "all",
			type = "all",
		)

		val json = webClient.httpGet(url).parseJson()
		val genresArray = json.getJSONArray("availableGenres")
		val genres = HashSet<MangaTag>(genresArray.length())

		for (i in 0 until genresArray.length()) {
			val genreName = genresArray.getString(i)
			genres.add(
				MangaTag(
					key = genreName.toTitleCase(sourceLocale),
					title = genreName.toTitleCase(sourceLocale),
					source = source,
				)
			)
		}
		genreCache = genres
		return genres
	}

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {

		val url = buildApiUrl(
			search = filter.query.orEmpty(),
			sort = when (order) {
				SortOrder.POPULARITY -> "popular"
				SortOrder.NEWEST -> "new"
				else -> "popular"
			},
			status = filter.states.oneOrThrowIfMany()?.let {
				when (it) {
					MangaState.ONGOING -> "ongoing"
					MangaState.FINISHED -> "completed"
					MangaState.PAUSED -> "hiatus"
					else -> "all"
				}
			} ?: "all",
			genre = filter.tags.oneOrThrowIfMany()?.key ?: "all",
			type = filter.types.oneOrThrowIfMany()?.let {
				when (it) {
					ContentType.MANGA -> "Manga"
					ContentType.MANHWA -> "Manhwa"
					ContentType.MANHUA -> "Manhua"
					else -> "all"
				}
			} ?: "all",
		)

		val json = webClient.httpGet(url).parseJson()

		return json.getJSONArray("manga").mapJSON { jo ->
			parseMangaFromList(jo)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val json = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseJson()
		val mangaJson = json.getJSONObject("manga")
		val mangaId = mangaJson.getInt("id")
		val chaptersUrl = "https://$domain/api/manga/$mangaId/chapters"
		val chaptersJson = webClient.httpGet(chaptersUrl).parseJson()
		val allChapters = parseAllChapters(chaptersJson, mangaId, mangaJson.optString("team_name").nullIfEmpty())

		return parseMangaDetails(mangaJson, allChapters)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val json = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseJson()
		return json.getJSONArray("pages").mapJSON { jo ->
			val url = jo.getString("url").toAbsoluteUrl(domain)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private fun buildApiUrl(
		search: String,
		sort: String,
		status: String,
		genre: String,
		type: String,
	): String = buildString {
		append("https://")
		append(domain)
		append("/api/manga/discover?")
		append("search=").append(search.urlEncoded())
		append("&sort=").append(sort)
		append("&status=").append(status)
		append("&genre=").append(genre.urlEncoded())
		append("&type=").append(type)
		append("&year=").append("all")
	}

	private fun parseMangaFromList(jo: JSONObject): Manga {
		val mangaId = jo.getInt("id")
		val mangaUrl = "/api/manga/$mangaId"
		val genres = jo.getJSONArray("genres")

		return Manga(
			id = generateUid(mangaUrl),
			url = mangaUrl,
			publicUrl = "https://$domain/manga/$mangaId",
			coverUrl = jo.getStringOrNull("cover_url")?.toAbsoluteUrl(domain),
			title = jo.getString("title"),
			altTitles = emptySet(),
			rating = jo.optDouble("rating", 0.0).let { if (it > 0) it.toFloat() / 10f else RATING_UNKNOWN },
			tags = parseGenreTags(genres),
			authors = emptySet(),
			state = parseStatus(jo.getString("status")),
			source = source,
			contentRating = if (genres.toString().contains("Adulte") || genres.toString().contains("Mature") || isNsfwSource) {
				ContentRating.ADULT
			} else null,
		)
	}

	private fun parseMangaDetails(mangaJson: JSONObject, chapters: List<MangaChapter>): Manga {
		val mangaId = mangaJson.getInt("id")
		val mangaUrl = "/api/manga/$mangaId"
		val genres = mangaJson.getJSONArray("genres")

		val authors = parseStringArray(mangaJson.optJSONArray("authors"))
		val artists = parseStringArray(mangaJson.optJSONArray("artists"))

		return Manga(
			id = generateUid(mangaUrl),
			url = mangaUrl,
			publicUrl = "https://$domain/manga/$mangaId",
			coverUrl = mangaJson.getStringOrNull("cover_url")?.toAbsoluteUrl(domain),
			title = mangaJson.getString("title"),
			altTitles = parseStringArray(mangaJson.optJSONArray("alternative_titles")),
			rating = mangaJson.optDouble("rating", 0.0).let { if (it > 0) it.toFloat() / 10f else RATING_UNKNOWN },
			tags = parseGenreTags(genres),
			authors = authors + artists,
			state = parseStatus(mangaJson.getString("status")),
			source = source,
			contentRating = if (genres.toString().contains("Adulte") || genres.toString().contains("Mature")) {
				ContentRating.ADULT
			} else null,
			description = mangaJson.getString("description"),
			chapters = chapters,
		)
	}

	private fun parseAllChapters(json: JSONObject, mangaId: Int, teamName: String?): List<MangaChapter> {
		return json.getJSONArray("chapters").mapJSON { jo ->
			val chapterId = jo.getInt("id")
			val chapterUrl = "/api/manga/$mangaId/chapters/$chapterId/pages"
			val numberStr = jo.getString("number")

			val chapterNumber = numberStr.substringBefore('.').substringBefore(' ').toFloatOrNull() ?: 0f

			MangaChapter(
				id = generateUid(chapterUrl),
				title = jo.optString("title").nullIfEmpty(),
				number = chapterNumber,
				volume = 0,
				url = chapterUrl,
				scanlator = teamName,
				uploadDate = parseDate(jo.getString("date")),
				branch = null,
				source = source,
			)
		}.reversed()
	}

	private fun parseGenreTags(genresArray: org.json.JSONArray): Set<MangaTag> {
		val tags = HashSet<MangaTag>(genresArray.length())
		for (i in 0 until genresArray.length()) {
			val genreName = genresArray.getString(i)
			tags.add(
				MangaTag(
					key = genreName.toTitleCase(),
					title = genreName.toTitleCase(),
					source = source,
				)
			)
		}
		return tags
	}

	private fun parseStringArray(array: org.json.JSONArray?): Set<String> {
		if (array == null) return emptySet()
		val result = HashSet<String>(array.length())
		for (i in 0 until array.length()) {
			result.add(array.getString(i))
		}
		return result
	}

	private fun parseStatus(status: String): MangaState? = when (status) {
		"ongoing" -> MangaState.ONGOING
		"completed" -> MangaState.FINISHED
		"hiatus" -> MangaState.PAUSED
		else -> null
	}

	private fun parseDate(dateStr: String): Long {
		return SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH).parse(dateStr)
			?.time ?: 0L
	}

	override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? {
		val mangaId = link.pathSegments.lastOrNull()?.toIntOrNull() ?: return null
		val apiUrl = "/api/manga/$mangaId"
		return resolver.resolveManga(this, url = apiUrl, id = generateUid(apiUrl))
	}
}
