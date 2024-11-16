package org.koitharu.kotatsu.parsers.site.ru.rulib

import androidx.collection.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.text.SimpleDateFormat
import java.util.*

internal abstract class LibSocialParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	protected val siteDomain: String,
	protected val siteId: Int,
) : PagedMangaParser(context, source, pageSize = 60) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.RATING,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
	)

	final override val configKeyDomain = ConfigKey.Domain(siteDomain)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.allOf(MangaState::class.java),
	)

	private val statesMap = intObjectMapOf(
		1, MangaState.ONGOING,
		2, MangaState.FINISHED,
		3, MangaState.UPCOMING,
		4, MangaState.PAUSED,
		5, MangaState.ABANDONED,
	)
	private val imageServers = suspendLazy(initializer = ::fetchServers)
	private val splitTranslationsKey = ConfigKey.SplitByTranslations(true)
	private val preferredServerKey = ConfigKey.PreferredImageServer(
		presetValues = mapOf(
			null to null,
			SERVER_MAIN to "Первый",
			SERVER_SECONDARY to "Второй",
			SERVER_COMPRESS to "Сжатия",
			SERVER_DOWNLOAD to "Загрузки",
			SERVER_CROP to "Обрезки",
		),
		defaultValue = null,
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val urlBuilder = HttpUrl.Builder()
			.scheme("https")
			.host("api.lib.social")
			.addPathSegment("api")
			.addPathSegment("manga")
			.addQueryParameter("site_id[]", siteId.toString())
			.addQueryParameter("fields[]", "rate")
			.addQueryParameter("fields[]", "rate_avg")
			.addQueryParameter("page", page.toString())
		for (state in filter.states) {
			urlBuilder.addQueryParameter("status[]", statesMap.keyOf(state).toString())
		}
		for (tag in filter.tags) {
			urlBuilder.addQueryParameter("${tag.typeKey()}[]", tag.key.drop(1))
		}
		for (tag in filter.tagsExclude) {
			urlBuilder.addQueryParameter("${tag.typeKey()}_exclude[]", tag.key.drop(1))
		}
		if (!filter.query.isNullOrEmpty()) {
			urlBuilder.addQueryParameter("q", filter.query)
		}
		urlBuilder.addQueryParameter(
			"sort_by",
			when (order) {
				SortOrder.UPDATED -> "last_chapter_at"
				SortOrder.POPULARITY -> "views"
				SortOrder.RATING -> "rate_avg"
				SortOrder.NEWEST -> "created_at"
				SortOrder.ALPHABETICAL,
				SortOrder.ALPHABETICAL_DESC,
					-> "rus_name"

				else -> null
			},
		)
		urlBuilder.addQueryParameter(
			"sort_type",
			when (order) {
				SortOrder.UPDATED,
				SortOrder.POPULARITY,
				SortOrder.RATING,
				SortOrder.NEWEST,
				SortOrder.ALPHABETICAL_DESC,
					-> "desc"

				SortOrder.ALPHABETICAL -> "asc"
				else -> null
			},
		)
		val json = webClient.httpGet(urlBuilder.build()).parseJson()
		val data = json.getJSONArray("data")
		return data.mapJSON(::parseManga)
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val chapters = async { fetchChapters(manga) }
		val url = HttpUrl.Builder()
			.scheme("https")
			.host("api.lib.social")
			.addPathSegment("api")
			.addPathSegment("manga")
			.addPathSegment(manga.url)
			.addQueryParameter("fields[]", "summary")
			.addQueryParameter("fields[]", "genres")
			.addQueryParameter("fields[]", "tags")
			.addQueryParameter("fields[]", "authors")
			.build()
		val json = webClient.httpGet(url).parseJson().getJSONObject("data")
		val genres = json.getJSONArray("genres").mapJSON { jo ->
			MangaTag(title = jo.getString("name"), key = "g" + jo.getInt("id"), source = source)
		}
		val tags = json.getJSONArray("genres").mapJSON { jo ->
			MangaTag(title = jo.getString("name"), key = "t" + jo.getInt("id"), source = source)
		}
		manga.copy(
			title = json.getStringOrNull("rus_name") ?: manga.title,
			altTitle = json.getString("name"),
			tags = tagsSetOf(tags, genres),
			author = json.getJSONArray("authors").optJSONObject(0)?.getStringOrNull("name"),
			description = json.getString("summary").nl2br(),
			chapters = chapters.await(),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> = coroutineScope {
		val pages = async {
			webClient.httpGet(
				concatUrl("https://api.lib.social/api/manga/", chapter.url),
			).parseJson().getJSONObject("data")
		}
		val servers = imageServers.get()
		val json = pages.await()
		val primaryServer = getPrimaryImageServer(servers)
		json.getJSONArray("pages").mapJSON { jo ->
			val url = jo.getString("url")
			MangaPage(
				id = generateUid(jo.getLong("id")),
				url = concatUrl(primaryServer, url),
				preview = servers[SERVER_COMPRESS]?.let { concatUrl(it, url) },
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> = coroutineScope {
		val tags = async { fetchTags("tags") }
		val genres = async { fetchTags("genres") }
		tagsSetOf(tags.await(), genres.await())
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val json = webClient.httpGet(
			HttpUrl.Builder()
				.scheme("https")
				.host("api.lib.social")
				.addPathSegment("api")
				.addPathSegment("manga")
				.addPathSegment(seed.url)
				.addPathSegment("similar")
				.build(),
		).parseJson().getJSONArray("data")
		return json.mapJSON { jo ->
			parseManga(jo.getJSONObject("media"))
		}
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.remove(configKeyDomain)
		keys.add(splitTranslationsKey)
		keys.add(preferredServerKey)
	}

	private fun parseManga(jo: JSONObject): Manga {
		val cover = jo.getJSONObject("cover")
		return Manga(
			id = generateUid(jo.getLong("id")),
			title = jo.getString("rus_name").ifEmpty { jo.getString("name") },
			altTitle = jo.getString("name"),
			url = jo.getString("slug_url"),
			publicUrl = "https://$siteDomain/ru/manga/" + jo.getString("slug_url"),
			rating = jo.optJSONObject("rating")
				?.getFloatOrDefault("average", RATING_UNKNOWN * 10f)?.div(10f) ?: RATING_UNKNOWN,
			isNsfw = jo.getJSONObject("ageRestriction").getIntOrDefault("id", 0) >= 3,
			coverUrl = cover.getString("thumbnail"),
			tags = setOf(),
			state = statesMap[jo.optJSONObject("status")?.getIntOrDefault("id", -1) ?: -1],
			author = null,
			largeCoverUrl = cover.getString("default"),
			source = source,
		)
	}

	private fun getPrimaryImageServer(servers: ScatterMap<String, String>): String {
		val preferred = config[preferredServerKey]
		if (preferred != null) {
			servers[preferred]?.let { return it }
		}
		return checkNotNull(servers[SERVER_MAIN] ?: servers[SERVER_DOWNLOAD] ?: servers[SERVER_SECONDARY]) {
			"No available images servers"
		}
	}

	private suspend fun fetchChapters(manga: Manga): List<MangaChapter> {
		val url = HttpUrl.Builder()
			.scheme("https")
			.host("api.lib.social")
			.addPathSegment("api")
			.addPathSegment("manga")
			.addPathSegment(manga.url)
			.addPathSegment("chapters")
			.build()
		val json = webClient.httpGet(url).parseJson().getJSONArray("data")
		val builder = ChaptersListBuilder(json.length())
		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
		val useBranching = config[splitTranslationsKey]
		for (i in 0 until json.length()) {
			val jo = json.getJSONObject(i)
			val volume = jo.getIntOrDefault("volume", 0)
			val number = jo.getFloatOrDefault("number", 0f)
			val numberString = number.formatSimple()
			val name = jo.getStringOrNull("name") ?: buildString {
				if (volume > 0) append("Том ").append(volume).append(' ')
				append("Глава ").append(numberString)
			}
			val branches = jo.getJSONArray("branches")
			for (j in 0 until branches.length()) {
				val bjo = branches.getJSONObject(j)
				val id = bjo.getLong("id")
				val team = bjo.getJSONArray("teams").optJSONObject(0)?.getStringOrNull("name")
				builder += MangaChapter(
					id = generateUid(id),
					name = name,
					number = number,
					volume = volume,
					url = "${manga.url}/chapter?number=$numberString&volume=$volume",
					scanlator = team,
					uploadDate = dateFormat.tryParse(bjo.getStringOrNull("created_at")),
					branch = if (useBranching) team else null,
					source = source,
				)
			}
		}
		return builder.toList()
	}

	private suspend fun fetchTags(type: String): List<MangaTag> {
		val data = webClient.httpGet(
			HttpUrl.Builder()
				.scheme("https")
				.host("api.lib.social")
				.addPathSegment("api").addPathSegment(type).build(),
		).parseJson().getJSONArray("data")
		val prefix = type.first().toString()
		return data.mapJSONNotNull { jo ->
			val sites = jo.getJSONArray("site_ids").toIntSet()
			if (siteId !in sites) {
				return@mapJSONNotNull null
			}
			MangaTag(
				title = jo.getString("name"),
				key = prefix + jo.getInt("id"),
				source = source,
			)
		}
	}

	private suspend fun fetchServers(): ScatterMap<String, String> {
		val json = webClient.httpGet(
			HttpUrl.Builder()
				.scheme("https")
				.host("api.lib.social")
				.addPathSegment("api")
				.addPathSegment("constants")
				.addQueryParameter("fields[]", "imageServers")
				.build(),
		).parseJson().getJSONObject("data").getJSONArray("imageServers")
		val result = MutableScatterMap<String, String>()
		for (i in 0 until json.length()) {
			val jo = json.getJSONObject(i)
			val sites = jo.getJSONArray("site_ids").toIntSet()
			if (siteId !in sites) {
				continue
			}
			result[jo.getString("id")] = jo.getString("url")
		}
		return result
	}

	private fun <V> IntObjectMap<V>.keyOf(value: V): Int {
		forEach { k, v ->
			if (v == value) {
				return k
			}
		}
		throw NoSuchElementException("No key associated with value $value")
	}

	private fun JSONArray.toIntSet(): IntSet {
		val result = MutableIntSet(length())
		for (i in 0 until length()) {
			result.add(getInt(i))
		}
		return result
	}

	private fun MangaTag.typeKey() = when (key.firstOrNull()) {
		'g' -> "genres"
		't' -> "tags"
		else -> throw IllegalArgumentException("Tag $key($title) is of unknown type")
	}

	private fun tagsSetOf(tags: Collection<MangaTag>, genres: Collection<MangaTag>): Set<MangaTag> {
		val result = ArraySet<MangaTag>(tags.size + genres.size)
		val names = HashSet<String>(tags.size + genres.size)
		genres.forEach { x -> if (names.add(x.title)) result.add(x) }
		tags.forEach { x -> if (names.add(x.title)) result.add(x) }
		return result
	}

	protected companion object {

		const val SERVER_MAIN = "main"
		const val SERVER_SECONDARY = "secondary"
		const val SERVER_COMPRESS = "compress"
		const val SERVER_DOWNLOAD = "download"
		const val SERVER_CROP = "crop"
	}
}
