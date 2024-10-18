package org.koitharu.kotatsu.parsers.site.all

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 20
private const val CHAPTERS_FIRST_PAGE_SIZE = 120
private const val CHAPTERS_MAX_PAGE_SIZE = 500
private const val CHAPTERS_PARALLELISM = 3
private const val CHAPTERS_MAX_COUNT = 10_000 // strange api behavior, looks like a bug
private const val LOCALE_FALLBACK = "en"
private const val SERVER_DATA = "data"
private const val SERVER_DATA_SAVER = "data-saver"

@MangaSourceParser("MANGADEX", "MangaDex")
internal class MangaDexParser(context: MangaLoaderContext) : MangaParser(context, MangaParserSource.MANGADEX) {

	override val configKeyDomain = ConfigKey.Domain("mangadex.org")

	private val preferredServerKey = ConfigKey.PreferredImageServer(
		presetValues = mapOf(
			SERVER_DATA to "Original quality",
			SERVER_DATA_SAVER to "Compressed quality",
		),
		defaultValue = SERVER_DATA,
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
		keys.add(preferredServerKey)
	}

	override val availableSortOrders: EnumSet<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.UPDATED_ASC,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_ASC,
		SortOrder.RATING,
		SortOrder.RATING_ASC,
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
		SortOrder.ADDED,
		SortOrder.ADDED_ASC,
		SortOrder.RELEVANCE,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isYearSupported = true,
			isOriginalLocaleSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions = coroutineScope {
		val localesDeferred = async { fetchAvailableLocales() }
		val tagsDeferred = async { fetchAvailableTags() }
		MangaListFilterOptions(
			availableTags = tagsDeferred.await(),
			availableStates = EnumSet.of(
				MangaState.ONGOING,
				MangaState.FINISHED,
				MangaState.PAUSED,
				MangaState.ABANDONED,
			),
			availableContentRating = EnumSet.allOf(ContentRating::class.java),
			availableDemographics = EnumSet.of(
				Demographic.SHOUNEN,
				Demographic.SHOUJO,
				Demographic.SEINEN,
				Demographic.JOSEI,
				Demographic.NONE,
			),
			availableLocales = localesDeferred.await(),
		)
	}

	override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val domain = domain
		val url = buildString {
			append("https://api.")
			append(domain)
			append("/manga?limit=")
			append(PAGE_SIZE)
			append("&offset=")
			append(offset)
			append("&includes[]=cover_art&includes[]=author&includes[]=artist")

			filter.query?.let {
				append("&title=")
				append(filter.query.urlEncoded())
			}

			filter.tags.forEach {
				append("&includedTags[]=")
				append(it.key)
			}

			filter.tagsExclude.forEach {
				append("&excludedTags[]=")
				append(it.key)
			}

			if (filter.contentRating.isNotEmpty()) {
				filter.contentRating.forEach {
					when (it) {
						ContentRating.SAFE -> append("&contentRating[]=safe")
						ContentRating.SUGGESTIVE -> append("&contentRating[]=suggestive&contentRating[]=erotica")
						ContentRating.ADULT -> append("&contentRating[]=pornographic")

					}
				}
			} else append("&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica&contentRating[]=pornographic")

			append("&order")
			append(
				when (order) {
					SortOrder.UPDATED -> "[latestUploadedChapter]=desc"
					SortOrder.UPDATED_ASC -> "[latestUploadedChapter]=asc"
					SortOrder.RATING -> "[rating]=desc"
					SortOrder.RATING_ASC -> "[rating]=asc"
					SortOrder.ALPHABETICAL -> "[title]=asc"
					SortOrder.ALPHABETICAL_DESC -> "[title]=desc"
					SortOrder.NEWEST -> "[year]=desc"
					SortOrder.NEWEST_ASC -> "[year]=asc"
					SortOrder.POPULARITY -> "[followedCount]=desc"
					SortOrder.POPULARITY_ASC -> "[followedCount]=asc"
					SortOrder.ADDED -> "[createdAt]=desc"
					SortOrder.ADDED_ASC -> "[createdAt]=asc"
					SortOrder.RELEVANCE -> "&order[relevance]=desc"
					else -> "[latestUploadedChapter]=desc"
				},
			)

			filter.states.forEach {
				append("&status[]=")
				when (it) {
					MangaState.ONGOING -> append("ongoing")
					MangaState.FINISHED -> append("completed")
					MangaState.ABANDONED -> append("cancelled")
					MangaState.PAUSED -> append("hiatus")
					else -> append("")
				}
			}

			filter.demographics.forEach {
				append("&publicationDemographic[]=")
				append(
					when (it) {
						Demographic.SHOUNEN -> "shounen"
						Demographic.SHOUJO -> "shoujo"
						Demographic.SEINEN -> "seinen"
						Demographic.JOSEI -> "josei"
						Demographic.NONE -> "none"
						else -> ""
					},
				)
			}

			filter.locale?.let {
				append("&availableTranslatedLanguage[]=")
				if (it.language == "in") {
					append("id")
				} else {
					append(it.language)
				}
			}

			filter.originalLocale?.let {
				append("&originalLanguage[]=")
				if (it.language == "in") {
					append("id")
				} else {
					append(it.language)
				}
			}

			if (filter.year != 0) {
				append("&year=")
				append(filter.year)
			}
		}
		val json = webClient.httpGet(url).parseJson().getJSONArray("data")
		return json.mapJSON { jo -> jo.fetchManga(null) }
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val mangaId = manga.url.removePrefix("/")
		return getDetails(mangaId)
	}

	override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? {
		val regex = Regex("[0-9a-f\\-]{10,}", RegexOption.IGNORE_CASE)
		val mangaId = link.pathSegments.find { regex.matches(it) } ?: return null
		return getDetails(mangaId)
	}

	private suspend fun getDetails(mangaId: String): Manga = coroutineScope {
		val jsonDeferred = async {
			webClient.httpGet(
				"https://api.$domain/manga/${mangaId}?includes[]=artist&includes[]=author&includes[]=cover_art",
			).parseJson().getJSONObject("data")
		}
		val feedDeferred = async { loadChapters(mangaId) }
		jsonDeferred.await().fetchManga(mapChapters(feedDeferred.await()))
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val json = webClient.httpGet(
			"https://api.$domain/at-home/server/${chapter.url}?forcePort443=false",
		).parseJson()
		val chapterJson = json.getJSONObject("chapter")
		val server = config[preferredServerKey] ?: SERVER_DATA
		val pages = chapterJson.getJSONArray(
			if (server == SERVER_DATA_SAVER) "dataSaver" else "data",
		)
		val prefix = "${json.getString("baseUrl")}/$server/${chapterJson.getString("hash")}/"
		return List(pages.length()) { i ->
			val url = prefix + pages.getString(i)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null, // TODO prefix + dataSaver.getString(i),
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val tags = webClient.httpGet("https://api.${domain}/manga/tag").parseJson()
			.getJSONArray("data")
		return tags.mapJSONToSet { jo ->
			MangaTag(
				title = jo.getJSONObject("attributes").getJSONObject("name")
					.firstStringValue()
					.toTitleCase(Locale.ENGLISH),
				key = jo.getString("id"),
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableLocales(): Set<Locale> {
		val head = webClient.httpGet("https://$domain/").parseHtml().head()
		return head.getElementsByAttributeValue("property", "og:locale:alternate")
			.mapNotNullToSet { meta ->
				val raw = meta.attrOrNull("content") ?: return@mapNotNullToSet null
				Locale(raw.substringBefore('_'), raw.substringAfter('_', ""))
			}
	}

	private fun JSONObject.fetchManga(chapters: List<MangaChapter>?): Manga {
		val id = getString("id")
		val attrs = getJSONObject("attributes")
		val relations = getJSONArray("relationships").associateByKey("type")
		val cover = relations["cover_art"]
			?.getJSONObject("attributes")
			?.getString("fileName")
			?.let {
				"https://uploads.$domain/covers/$id/$it"
			}
		return Manga(
			id = generateUid(id),
			title = requireNotNull(attrs.getJSONObject("title").selectByLocale()) {
				"Title should not be null"
			},
			altTitle = attrs.optJSONArray("altTitles")?.flatten()?.selectByLocale(),
			url = id,
			publicUrl = "https://$domain/title/$id",
			rating = RATING_UNKNOWN,
			isNsfw = when (attrs.getStringOrNull("contentRating")) {
				"erotica", "pornographic" -> true
				else -> false
			},
			coverUrl = cover?.plus(".256.jpg").orEmpty(),
			largeCoverUrl = cover,
			description = attrs.optJSONObject("description")?.selectByLocale(),
			tags = attrs.getJSONArray("tags").mapJSONToSet { tag ->
				MangaTag(
					title = tag.getJSONObject("attributes")
						.getJSONObject("name")
						.firstStringValue()
						.toTitleCase(),
					key = tag.getString("id"),
					source = source,
				)
			},
			state = when (attrs.getStringOrNull("status")) {
				"ongoing" -> MangaState.ONGOING
				"completed" -> MangaState.FINISHED
				"hiatus" -> MangaState.PAUSED
				"cancelled" -> MangaState.ABANDONED
				else -> null
			},
			author = (relations["author"] ?: relations["artist"])
				?.getJSONObject("attributes")
				?.getStringOrNull("name"),
			chapters = chapters,
			source = source,
		)
	}

	private fun JSONObject.firstStringValue() = entries<String>().first().value

	private fun JSONObject.selectByLocale(): String? {
		val preferredLocales = context.getPreferredLocales()
		for (locale in preferredLocales) {
			getStringOrNull(locale.language)?.let { return it }
			getStringOrNull(locale.toLanguageTag())?.let { return it }
		}
		return getStringOrNull(LOCALE_FALLBACK) ?: entries<String>().firstOrNull()?.value
	}

	private fun JSONArray.flatten(): JSONObject {
		val result = JSONObject()
		repeat(length()) { i ->
			val jo = optJSONObject(i)
			if (jo != null) {
				for (key in jo.keys()) {
					result.put(key, jo.get(key))
				}
			}
		}
		return result
	}

	private suspend fun loadChapters(mangaId: String): List<JSONObject> {
		val firstPage = loadChapters(mangaId, offset = 0, limit = CHAPTERS_FIRST_PAGE_SIZE)
		if (firstPage.size >= firstPage.total) {
			return firstPage.data
		}
		val tail = coroutineScope {
			val leftCount = firstPage.total.coerceAtMost(CHAPTERS_MAX_COUNT) - firstPage.size
			val pages = (leftCount / CHAPTERS_MAX_PAGE_SIZE.toFloat()).toIntUp()
			val dispatcher = Dispatchers.Default.limitedParallelism(CHAPTERS_PARALLELISM)
			List(pages) { page ->
				val offset = page * CHAPTERS_MAX_PAGE_SIZE + firstPage.size
				async(dispatcher) {
					loadChapters(mangaId, offset, CHAPTERS_MAX_PAGE_SIZE)
				}
			}.awaitAll()
		}
		val result = ArrayList<JSONObject>(firstPage.total)
		result += firstPage.data
		tail.flatMapTo(result) { it.data }
		return result
	}

	private suspend fun loadChapters(mangaId: String, offset: Int, limit: Int): Chapters {
		val limitedLimit = when {
			offset >= CHAPTERS_MAX_COUNT -> return Chapters(emptyList(), CHAPTERS_MAX_COUNT)
			offset + limit > CHAPTERS_MAX_COUNT -> CHAPTERS_MAX_COUNT - offset
			else -> limit
		}
		val url = buildString {
			append("https://api.")
			append(domain)
			append("/manga/")
			append(mangaId)
			append("/feed")
			append("?limit=")
			append(limitedLimit)
			append("&includes[]=scanlation_group&order[volume]=asc&order[chapter]=asc&offset=")
			append(offset)
			append("&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica&contentRating[]=pornographic")
		}
		val json = webClient.httpGet(url).parseJson()
		if (json.getString("result") == "ok") {
			return Chapters(
				data = json.optJSONArray("data")?.asTypedList<JSONObject>().orEmpty(),
				total = json.getInt("total"),
			)
		} else {
			val error = json.optJSONArray("errors").mapJSON { jo ->
				jo.getString("detail")
			}.joinToString("\n")
			throw ParseException(error, url)
		}
	}

	private fun mapChapters(list: List<JSONObject>): List<MangaChapter> {
		// 2022-01-02T00:27:11+00:00
		val dateFormat = SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ss'+00:00'",
			Locale.ROOT,
		)
		val chaptersBuilder = ChaptersListBuilder(list.size)
		val branchedChapters = HashMap<String?, HashMap<Pair<Int, Float>, MangaChapter>>()
		for (jo in list) {
			val id = jo.getString("id")
			val attrs = jo.getJSONObject("attributes")
			if (!attrs.isNull("externalUrl")) {
				continue
			}
			val number = attrs.getFloatOrDefault("chapter", 0f)
			val volume = attrs.getIntOrDefault("volume", 0)
			val locale = attrs.getStringOrNull("translatedLanguage")?.let { Locale.forLanguageTag(it) }
			val lc = locale?.getDisplayName(locale)?.toTitleCase(locale)
			val relations = jo.getJSONArray("relationships").associateByKey("type")
			val team = relations["scanlation_group"]?.optJSONObject("attributes")?.getStringOrNull("name")
			val branch = (list.indices).firstNotNullOf { i ->
				val b = if (i == 0) lc else "$lc ($i)"
				if (branchedChapters[b]?.get(volume to number) == null) b else null
			}
			val chapter = MangaChapter(
				id = generateUid(id),
				name = attrs.getStringOrNull("title") ?: buildString {
					if (volume > 0) append("Vol. ").append(volume).append(' ')
					append("Chapter ").append(number.formatSimple())
				},
				number = number,
				volume = volume,
				url = id,
				scanlator = team,
				uploadDate = dateFormat.tryParse(attrs.getString("publishAt")),
				branch = branch,
				source = source,
			)
			if (chaptersBuilder.add(chapter)) {
				branchedChapters.getOrPut(branch, ::HashMap)[volume to number] = chapter
			}
		}
		return chaptersBuilder.toList()
	}

	private fun JSONArray.associateByKey(key: String): Map<String, JSONObject> {
		val destination = LinkedHashMap<String, JSONObject>(length())
		repeat(length()) { i ->
			val item = getJSONObject(i)
			val keyValue = item.getString(key)
			destination[keyValue] = item
		}
		return destination
	}

	private class Chapters(
		val data: List<JSONObject>,
		val total: Int,
	) {

		val size: Int
			get() = data.size
	}
}
