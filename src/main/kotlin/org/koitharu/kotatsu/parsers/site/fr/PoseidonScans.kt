package org.koitharu.kotatsu.parsers.site.fr

import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.SinglePageMangaParser
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
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseSafe
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale
import java.util.TimeZone

@Broken("The source to change structure")
@MangaSourceParser("POSEIDONSCANS", "Poseidon Scans", "fr")
internal class PoseidonScans(context: MangaLoaderContext) :
	SinglePageMangaParser(context, MangaParserSource.POSEIDONSCANS) {

	override val configKeyDomain = ConfigKey.Domain("poseidonscans.com")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.ALPHABETICAL,
		SortOrder.POPULARITY,
		SortOrder.RATING,
	)

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
		isSearchSupported = true,
		isSearchWithFiltersSupported = true,
		isMultipleTagsSupported = true,
		isAuthorSearchSupported = true,
		isTagsExclusionSupported = true,
	)

	private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.FRENCH).apply {
		timeZone = TimeZone.getTimeZone("UTC")
	}

	private val nextFPushRegex =
		Regex("""self\.__next_f\.push\(\s*\[\s*1\s*,\s*"(.*)"\s*]\s*\)""", RegexOption.DOT_MATCHES_ALL)


	// Helper data class to hold extra info for sorting
	private data class MangaCache(
		val manga: Manga,
		val viewCount: Int,
		val type: ContentType,
		val latestChapterDate: Long,
	)

	// Cache all manga for local search and sorting
	private val allMangaCache = suspendLazy {
		val doc = webClient.httpGet("https://$domain/series").parseHtml()
		extractFullMangaListFromDocument(doc)
	}

	// Cache all available tags for filtering
	private val allTagsCache = suspendLazy {
		allMangaCache.get()
			.flatMap { it.manga.tags }
			.toSet()
	}

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = allTagsCache.get(),
		availableStates = EnumSet.of(
			MangaState.ONGOING,
			MangaState.FINISHED,
			MangaState.ABANDONED,
			MangaState.PAUSED,
		),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
		),
	)

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		var mangaCacheList = allMangaCache.get()

		if (!filter.query.isNullOrEmpty()) {
			val query = filter.query.lowercase(sourceLocale)
			mangaCacheList = mangaCacheList.filter { cachedManga ->
				cachedManga.manga.title.lowercase(sourceLocale)
					.contains(query) || cachedManga.manga.altTitles.any { it.lowercase(sourceLocale).contains(query) }
			}
		}

		if (!filter.author.isNullOrEmpty()) {
			mangaCacheList = mangaCacheList.filter { cachedManga ->
				cachedManga.manga.authors.any {
					it.lowercase(sourceLocale).contains(filter.author.lowercase(sourceLocale))
				}
			}
		}

		if (filter.states.isNotEmpty()) {
			mangaCacheList = mangaCacheList.filter { filter.states.contains(it.manga.state) }
		}

		if (filter.types.isNotEmpty()) {
			mangaCacheList = mangaCacheList.filter { filter.types.contains(it.type) }
		}

		// Tag inclusion filter
		if (filter.tags.isNotEmpty()) {
			mangaCacheList = mangaCacheList.filter { it.manga.tags.containsAll(filter.tags) }
		}

		// Tag exclusion filter
		if (filter.tagsExclude.isNotEmpty()) {
			mangaCacheList = mangaCacheList.filter { cachedManga ->
				!cachedManga.manga.tags.any { tag -> filter.tagsExclude.contains(tag) }
			}
		}

		// Sorting
		val sortedCachedMangaList = when (order) {
			SortOrder.UPDATED -> mangaCacheList.sortedByDescending { it.latestChapterDate }
			SortOrder.UPDATED_ASC -> mangaCacheList.sortedBy { it.latestChapterDate }
			SortOrder.ALPHABETICAL -> mangaCacheList.sortedBy { it.manga.title.lowercase() }
			SortOrder.ALPHABETICAL_DESC -> mangaCacheList.sortedByDescending { it.manga.title.lowercase() }
			SortOrder.POPULARITY -> mangaCacheList.sortedByDescending { it.viewCount }
			SortOrder.POPULARITY_ASC -> mangaCacheList.sortedBy { it.viewCount }
			SortOrder.RATING -> mangaCacheList.sortedByDescending { it.manga.rating }
			SortOrder.RATING_ASC -> mangaCacheList.sortedBy { it.manga.rating }
			else -> mangaCacheList
		}

		return sortedCachedMangaList.map { it.manga }
	}

	private fun extractFullMangaListFromDocument(doc: Document): List<MangaCache> {
		val pageData = extractNextJsPageData(doc)

		val mangasArray = pageData?.let {
			it.optJSONArray("mangas") ?: it.optJSONArray("series") ?: it.optJSONObject("initialData")
				?.optJSONArray("mangas") ?: it.optJSONObject("initialData")?.optJSONArray("series")
		} ?: return emptyList()

		return mangasArray.mapJSON { parseMangaDetailsFromJson(it) }

	}

	private fun parseMangaDetailsFromJson(mangaJson: JSONObject): MangaCache {
		val slug = mangaJson.getString("slug")
		val url = "/serie/$slug"
		val coverUrl = "https://$domain/api/covers/$slug.webp"

		val authors = mangaJson.getStringOrNull("author")?.takeIf { it.isNotBlank() && it != "null" }?.split(',')
			?.map(String::trim)?.toSet() ?: emptySet()

		val artists = mangaJson.getStringOrNull("artist")?.takeIf { it.isNotBlank() && it != "null" }?.split(',')
			?.map(String::trim)?.toSet() ?: emptySet()

		val altNamesString = mangaJson.optString("alternativeNames")
		val altTitles = altNamesString.split(',').map(String::trim).filter { it.isNotEmpty() && it != "null" }.toSet()

		val genresArray = mangaJson.optJSONArray("categories")
		val genres = mutableSetOf<MangaTag>()
		if (genresArray != null) {
			for (i in 0 until genresArray.length()) {
				val genreObj = genresArray.optJSONObject(i)
				val genreName = genreObj?.optString("name")
				if (!genreName.isNullOrEmpty()) {
					genres.add(MangaTag(key = genreName, title = genreName, source = source))
				}
			}
		}

		val ratingValue = mangaJson.optDouble("rating").toFloat()
		val rating = if (ratingValue > 0f) ratingValue.div(5f) else RATING_UNKNOWN
		val nsfw = mangaJson.optBoolean("isExplicit", false)

		val manga = Manga(
			id = generateUid(url),
			title = mangaJson.getString("title"),
			altTitles = altTitles,
			url = url,
			publicUrl = url.toAbsoluteUrl(domain),
			rating = rating,
			contentRating = if (nsfw) ContentRating.ADULT else ContentRating.SAFE,
			coverUrl = coverUrl,
			tags = genres,
			state = parseStatus(mangaJson.optString("status")),
			authors = authors + artists,
			description = mangaJson.getStringOrNull("description")
				?.takeIf { it.isNotBlank() && it != "null" && it != "Aucune description." },
			source = source,
		)
		val viewCount = mangaJson.optInt("viewCount", 0)
		val latestChapterDate = parseDate(mangaJson.optString("latestChapterCreatedAt"))
		val type = when (mangaJson.optString("type", "").lowercase(sourceLocale)) {
			"manhwa" -> ContentType.MANHWA
			"manhua" -> ContentType.MANHUA
			"webtoon" -> ContentType.MANHWA
			else -> ContentType.MANGA
		}

		return MangaCache(
			manga = manga,
			viewCount = viewCount,
			latestChapterDate = latestChapterDate,
			type = type,
		)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		if (!manga.chapters.isNullOrEmpty()) {
			return manga
		}

		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()

		val pageData = extractNextJsPageData(doc) ?: throw Exception("Could not extract Next.js data for manga details")

		val mangaDetailsJson =
			pageData.optJSONObject("manga") ?: pageData.optJSONObject("initialData")?.optJSONObject("manga")
			?: pageData.takeIf { it.has("slug") && it.has("title") }
			?: throw Exception("JSON 'manga' structure not found")

		// All details are already in the manga object. We only need to fetch the chapters.
		val chapters = extractChaptersFromMangaData(mangaDetailsJson, manga)

		return manga.copy(chapters = chapters)
	}

	private fun extractChaptersFromMangaData(mangaJson: JSONObject, manga: Manga): List<MangaChapter> {
		val chaptersArray = mangaJson.optJSONArray("chapters") ?: return emptyList()

		val slug = manga.url.substringAfterLast("/serie/").substringBefore("/")

		return chaptersArray.mapJSONNotNull { chapterJson ->
			val chapterNumber = chapterJson.optDouble("number", 0.0).toFloat()
			val title = chapterJson.optString("title", "")
			if (chapterJson.optBoolean("isPremium", false)) return@mapJSONNotNull null // Skip premium chapters

			val createdAt = chapterJson.optString("createdAt", "").takeIf(String::isNotEmpty)
				?: chapterJson.optString("publishedAt", "")

			val chapterUrl = "/serie/$slug/chapter/${chapterNumber.toInt()}"
			val uploadDate = parseDate(createdAt)

			val chapterTitle = if (title.isNotBlank() && title != "null") {
				"Chapitre ${formatChapterNumber(chapterNumber)} - $title"
			} else {
				"Chapitre ${formatChapterNumber(chapterNumber)}"
			}

			MangaChapter(
				id = generateUid(chapterUrl),
				title = chapterTitle,
				number = chapterNumber,
				volume = 0,
				url = chapterUrl,
				uploadDate = uploadDate,
				source = source,
				scanlator = null,
				branch = null,
			)
		}.reversed()
	}

	fun formatChapterNumber(number: Float): String {
		return if (number % 1 == 0f) {
			number.toInt().toString()
		} else {
			number.toString()
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()

		val pageData = extractNextJsPageData(doc) ?: throw Exception("Could not extract Next.js data for chapter pages")

		val imagesArray =
			pageData.optJSONObject("initialData")?.optJSONArray("images") ?: pageData.optJSONArray("images")
			?: throw Exception("Could not find 'images' array in page data")

		return imagesArray.mapJSON { imageJson ->
			val imageUrl = imageJson.getString("originalUrl")
			MangaPage(
				id = generateUid(imageUrl),
				url = imageUrl.toAbsoluteUrl(domain),
				preview = null,
				source = chapter.source,
			)
		}
	}

	private fun extractNextJsPageData(document: Document): JSONObject? {
		try {
			var foundRelevantObject: JSONObject? = null
			for (script in document.select("script")) {
				val scriptContent = script.data()
				if (!scriptContent.contains("self.__next_f.push")) continue

				val matches = nextFPushRegex.findAll(scriptContent)
				for (matchResult in matches) {
					if (matchResult.groupValues.size < 2) continue

					val rawDataString = matchResult.groupValues[1]
					val cleanedDataString = rawDataString.replace("\\\\", "\\").replace("\\\"", "\"")

					val patterns = listOf(
						"\"initialData\":{",
						"\"manga\":{",
						"\"mangas\":[",
						"\"series\":[",
						"\"chapter\":{",
						"\"images\":[",
					)

					for (pattern in patterns) {
						var searchIdx = -1
						while (true) {
							searchIdx = cleanedDataString.indexOf(pattern, startIndex = searchIdx + 1)
							if (searchIdx == -1) break

							// Find the start of the JSON object
							var objectStartIndex = -1
							var braceDepth = 0
							for (i in searchIdx downTo 0) {
								when (cleanedDataString[i]) {
									'}' -> braceDepth++
									'{' -> {
										if (braceDepth == 0) {
											objectStartIndex = i
											break
										}
										braceDepth--
									}
								}
							}

							if (objectStartIndex != -1) {
								val potentialJson = extractJsonObjectString(cleanedDataString, objectStartIndex)
								if (potentialJson != null) {
									try {
										val parsedContainer = JSONObject(potentialJson)
										foundRelevantObject = parsedContainer
										break
									} catch (_: Exception) {
										// Continue searching
									}
								}
							}
						}
						if (foundRelevantObject != null) break
					}
					if (foundRelevantObject != null) break
				}
				if (foundRelevantObject != null) break
			}

			return foundRelevantObject
		} catch (e: Exception) {
			error("General error in extractNextJsPageData: ${e.message}")
		}
	}

	private fun extractJsonObjectString(data: String, startIndex: Int): String? {
		if (startIndex < 0 || startIndex >= data.length || data[startIndex] != '{') return null

		var braceBalance = 1
		var inString = false
		var i = startIndex + 1
		while (i < data.length) {
			val char = data[i]
			when (char) {
				'\\' -> if (inString) i++
				'"' -> inString = !inString
				'{' -> if (!inString) braceBalance++
				'}' -> if (!inString) {
					braceBalance--
					if (braceBalance == 0) return data.substring(startIndex, i + 1)
				}
			}
			i++
		}
		return null
	}

	private fun parseStatus(status: String?): MangaState? {
		return when (status?.trim()?.lowercase(sourceLocale)) {
			"en cours", "ongoing" -> MangaState.ONGOING
			"terminé", "finished", "completed" -> MangaState.FINISHED
			"en pause", "hiatus", "paused" -> MangaState.PAUSED
			"annulé", "abandonné", "cancelled", "abandoned" -> MangaState.ABANDONED
			else -> null
		}
	}

	private fun parseDate(dateString: String?): Long {
		if (dateString.isNullOrBlank()) return 0L

		val cleanedDateString =
			dateString.removePrefix("\"").removeSuffix("\"").removePrefix("\$D").removePrefix("D").trim()

		return isoDateFormat.parseSafe(cleanedDateString)
	}
}
