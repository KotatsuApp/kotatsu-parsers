package org.koitharu.kotatsu.parsers.site.fr

import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
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
import org.koitharu.kotatsu.parsers.model.WordSet
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.getFloatOrDefault
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseSafe
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.EnumSet

@MangaSourceParser("PHENIXSCANS", "PhenixScans", "fr")
internal class PhenixscansParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.PHENIXSCANS, 18) {

	override val configKeyDomain = ConfigKey.Domain("phenix-scans.com")

	private val apiBaseUrl = "https://phenix-scans.com/api"

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isMultipleTagsSupported = true,
		)

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(
			SortOrder.UPDATED,
			SortOrder.ALPHABETICAL,
			SortOrder.POPULARITY,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/api/front/manga")

			when {
				!filter.query.isNullOrEmpty() -> {
					if (page > 1) {
						return emptyList()
					}
					append("/search?query=")
					append(filter.query.urlEncoded())
				}

				else -> {
					append("?page=")
					append(page.toString())
					append("&limit=18&sort=")
					append(
						when (order) {
							SortOrder.POPULARITY -> "rating"
							SortOrder.ALPHABETICAL -> "title"
							else -> "updatedAt"
						},
					)

					if (filter.tags.isNotEmpty()) {
						append("&genre=")
						filter.tags.joinTo(this, separator = ",") { it.key }
					}

					filter.types.oneOrThrowIfMany()?.let {
						append("&type=")
						append(
							when (it) {
								ContentType.MANGA -> "Manga"
								ContentType.MANHWA -> "Manhwa"
								ContentType.MANHUA -> "Manhua"
								else -> ""
							},
						)
					}

					if (filter.states.isNotEmpty()) {
						filter.states.oneOrThrowIfMany()?.let {
							append("&status=")
							when (it) {
								MangaState.ONGOING -> append("Ongoing")
								MangaState.FINISHED -> append("Completed")
								MangaState.PAUSED -> append("Hiatus")
								else -> append("")
							}
						}
					}

				}
			}
		}
		return parseMangaList(webClient.httpGet(url).parseJson().getJSONArray("mangas"))
	}

	private fun parseMangaList(json: JSONArray): List<Manga> {
		return json.mapJSON { j ->
			val slug = j.getString("slug")
			Manga(
				id = generateUid(j.getString("_id")),
				title = j.getString("title"),
				altTitles = emptySet(),
				url = "/manga/$slug",
				publicUrl = "https://$domain/manga/$slug",
				rating = j.getFloatOrDefault("averageRating", RATING_UNKNOWN * 10f) / 10f,
				contentRating = null,
				description = j.getStringOrNull("synopsis"),
				coverUrl = "$apiBaseUrl/${j.getString("coverImage")}",
				tags = emptySet(),
				state = when (j.getStringOrNull("status")) {
					"Ongoing" -> MangaState.ONGOING
					"Completed" -> MangaState.FINISHED
					"Hiatus" -> MangaState.PAUSED
					else -> null
				},
				authors = emptySet(),
				source = source,
			)
		}
	}

	private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", sourceLocale)

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val apiUrl = "$apiBaseUrl/front${manga.url}"
		val response = webClient.httpGet(apiUrl).parseJson()
		val mangaData = response.getJSONObject("manga")
		val chaptersArray = response.optJSONArray("chapters") ?: JSONArray()

		val coverImage = mangaData.getString("coverImage")
		val coverUrl = "$apiBaseUrl/$coverImage"

		val chaptersMap = LinkedHashMap<String, Pair<Long, MangaChapter>>(chaptersArray.length())

		for (i in 0 until chaptersArray.length()) {
			val chapterJson = chaptersArray.getJSONObject(i)
			val number = chapterJson.optString("number")
			val createdAt = chapterJson.optString("createdAt")
			val uploadDate = parseChapterDate(dateFormat, createdAt)

			val chapter = MangaChapter(
				id = generateUid(chapterJson.getString("_id")),
				title = "Chapitre $number",
				number = number.toFloatOrNull() ?: (i + 1f),
				volume = 0,
				url = "${manga.url}/$number",
				scanlator = null,
				uploadDate = uploadDate,
				branch = null,
				source = source,
			)

			// Keep the most recent version of duplicate chapters
			val existing = chaptersMap[number]
			if (existing == null || uploadDate > existing.first) {
				chaptersMap[number] = uploadDate to chapter
			}
		}

		val uniqueChapters = chaptersMap.values
			.map { it.second }
			.sortedBy { it.number }

		manga.copy(
			title = mangaData.getString("title"),
			altTitles = mangaData.optJSONArray("alternativeTitles")?.let { altArray ->
				(0 until altArray.length()).mapTo(mutableSetOf()) { altArray.getString(it) }
			} ?: emptySet(),
			tags = mangaData.optJSONArray("genres").mapJSONToSet { genreJson ->
				MangaTag(
					key = genreJson.getString("_id"),
					title = genreJson.getString("name").toTitleCase(),
					source = source,
				)
			},
			description = mangaData.getStringOrNull("synopsis"),
			rating = mangaData.getFloatOrDefault("averageRating", RATING_UNKNOWN * 10f) / 10f,
			coverUrl = coverUrl,
			state = when (mangaData.getStringOrNull("status")) {
				"Ongoing" -> MangaState.ONGOING
				"Completed" -> MangaState.FINISHED
				"Hiatus" -> MangaState.PAUSED
				else -> null
			},
			authors = mangaData.optJSONArray("authors")?.let { authorsArray ->
				(0 until authorsArray.length()).mapTo(mutableSetOf()) { authorsArray.getString(it) }
			} ?: emptySet(),
			chapters = uniqueChapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val slug = chapter.url.substringAfterLast("manga/").substringBeforeLast("/")
		val chapterNumber = chapter.url.substringAfterLast("/")

		val apiUrl = "$apiBaseUrl/front/manga/$slug/chapter/$chapterNumber"
		val response = webClient.httpGet(apiUrl).parseJson()

		val chapterData = response.getJSONObject("chapter")
		val imagesArray = chapterData.getJSONArray("images")
		println(imagesArray)
		return (0 until imagesArray.length()).map { i ->
			val imageUrl = imagesArray.getString(i)
			val url = "/api/$imageUrl"
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val json = webClient.httpGet("$apiBaseUrl/genres").parseJson()
			.getJSONArray("data")
		return json.mapJSONToSet {
			MangaTag(
				key = it.getString("_id"),
				title = it.getString("name").toTitleCase(sourceLocale),
				source = source,
			)
		}
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {

			WordSet(
				" sec", " min", " h", " j", " sem", " mois", " année",
			).endsWith(d) -> {
				parseRelativeDate(d)
			}

			WordSet("il y a").startsWith(d) -> {
				parseRelativeDate(d)
			}

			date.contains(Regex("""\d(st|nd|rd|th)""")) -> date.split(" ").map {
				if (it.contains(Regex("""\d\D\D"""))) {
					it.replace(Regex("""\D"""), "")
				} else {
					it
				}
			}.let { dateFormat.parseSafe(it.joinToString(" ")) }

			else -> dateFormat.parseSafe(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet("second", "sec")
				.anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis

			WordSet("min", "minute", "minutes")
				.anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis

			WordSet("heures", "heure", "h")
				.anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis

			WordSet("jour", "jours", "j")
				.anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("sem", "semaine", "semaines").anyWordIn(date) -> cal.apply {
				add(
					Calendar.WEEK_OF_YEAR,
					-number,
				)
			}.timeInMillis

			WordSet("mois")
				.anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis

			WordSet("année")
				.anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis

			else -> 0
		}
	}
}
