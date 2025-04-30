package org.koitharu.kotatsu.parsers.site.en

import androidx.collection.ArraySet
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacySinglePageMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.util.*
import java.util.concurrent.TimeUnit

@MangaSourceParser("FLAMECOMICS", "FlameComics", "en")
internal class FlameComics(context: MangaLoaderContext) :
	LegacySinglePageMangaParser(context, MangaParserSource.FLAMECOMICS) {

	private val commonPrefix = suspendLazy(initializer = ::fetchCommonPrefix)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override val configKeyDomain = ConfigKey.Domain("flamecomics.xyz")

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = urlBuilder()
		url.appendPathSegments("_next", "data", commonPrefix.get(), "browse.json")
		if (!filter.query.isNullOrEmpty()) {
			url.parameters.append("search", filter.query)
		}
		val json = webClient.httpGet(url.build()).parseJson().getJSONObject("pageProps").getJSONArray("series")
		return json.mapJSONNotNull { jo ->
			parseManga(jo).takeIf { it.tags.matches(filter) }
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = getDetailsImpl(manga.url.toLong())

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val (seriesId, token) = chapter.url.split('?')
		val url = urlBuilder()

		url.appendPathSegments("_next", "data", commonPrefix.get(), "series", seriesId, "$token.json")
		url.parameters.append("id", seriesId)
		url.parameters.append("token", token)
		val json = webClient.httpGet(url.build()).parseJson().getJSONObject("pageProps")
			.getJSONObject("chapter")
			.getJSONObject("images")
			.entries<JSONObject>()
		return json.map { (i, jo) ->
			MangaPage(
				id = generateUid("$i|$token"),
				url = imageUrl(seriesId, token + "/" + jo.getString("name"), 1920),
				preview = imageUrl(seriesId, token + "/" + jo.getString("name"), 128),
				source = source,
			)
		}
	}

	override suspend fun resolveLink(resolver: LinkResolver, link: Url): Manga? {
		val seriesId = link.segments.lastOrNull()?.toLongOrNull() ?: return null
		return getDetailsImpl(seriesId)
	}

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	private suspend fun fetchCommonPrefix(): String {
		val raw = webClient.httpGet(urlBuilder().build()).parseRaw()
		val regex = Regex("/_next/static/([^/]+)/_buildManifest\\.js")
		return checkNotNull(raw.findGroupValue(regex)) { "Unable to find common prefix" }
	}

	private fun imageUrl(seriesId: Any, url: String, width: Int) = urlBuilder().apply {
		appendPathSegments("_next", "image")
		parameters.append(
			"url",
			urlBuilder("cdn").apply {
				appendPathSegments("series", seriesId.toString(), url)
			}.buildString(),
		)
		parameters.append("w", width.toString())
		parameters.append("q", "100")
	}.buildString()

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val url = urlBuilder().apply {
			appendPathSegments("_next", "data", commonPrefix.get(), "browse.json")
		}.build()
		return webClient.httpGet(url).parseJson()
			.getJSONObject("pageProps")
			.getJSONArray("series")
			.mapJSONNotNull { it.getStringOrNull("categories") }
			.flatMapTo(ArraySet()) {
				JSONArray(it).asTypedList<String>().mapToSet { tagName -> tagName.toMangaTag() }
			}
	}

	private fun parseManga(jo: JSONObject): Manga {
		val seriesId = jo.getLong("series_id")
		val cover = jo.getStringOrNull("cover")
		val author = jo.getStringOrNull("author")
		return Manga(
			id = generateUid(seriesId),
			title = jo.getString("title"),
			altTitles = jo.getStringOrNull("altTitles")?.let {
				JSONArray(it).toStringSet()
			}.orEmpty(),
			url = seriesId.toString(),
			publicUrl = "https://${domain}/series/$seriesId",
			rating = RATING_UNKNOWN,
			contentRating = null,
			coverUrl = if (cover != null) {
				imageUrl(seriesId, cover, 256)
			} else {
				null
			},
			tags = jo.getStringOrNull("categories")?.let {
				JSONArray(it).asTypedList<String>().mapToSet { tagName -> tagName.toMangaTag() }
			}.orEmpty(),
			state = when (jo.getStringOrNull("status")) {
				"Dropped" -> MangaState.ABANDONED
				"Completed" -> MangaState.FINISHED
				"Hiatus" -> MangaState.PAUSED
				"Ongoing" -> MangaState.ONGOING
				else -> null
			},
			authors = setOfNotNull(author),
			largeCoverUrl = if (cover != null) {
				imageUrl(seriesId, cover, 720)
			} else {
				null
			},
			description = jo.getStringOrNull("description"),
			source = source,
		)
	}

	private suspend fun getDetailsImpl(seriesId: Long): Manga {
		val url = urlBuilder().apply {
			appendPathSegments("_next", "data", commonPrefix.get(), "series", "$seriesId.json")
			parameters.append("id", seriesId.toString())
		}.build()
		val json = webClient.httpGet(url).parseJson().getJSONObject("pageProps")
		val series = json.getJSONObject("series")
		val chapters = json.getJSONArray("chapters")
		return parseManga(series).copy(
			chapters = chapters.mapJSON { jo ->
				val chapterId = jo.getLong("chapter_id")
				val number = jo.getFloatOrDefault("chapter", 0f)
				MangaChapter(
					id = generateUid(longOf(seriesId.toInt(), chapterId.toInt())),
					title = jo.getStringOrNull("name"),
					number = number,
					volume = 0,
					url = seriesId.toString() + "?" + jo.getStringOrNull("token").orEmpty(),
					scanlator = null,
					uploadDate = TimeUnit.SECONDS.toMillis(jo.getLongOrDefault("release_date", 0L)),
					branch = jo.getStringOrNull("language"),
					source = source,
				)
			}.reversed(),
		)
	}

	private fun Set<MangaTag>.matches(filter: MangaListFilter): Boolean {
		if (filter.tags.isNotEmpty() && !containsAll(filter.tags)) {
			return false
		}
		for (tag in filter.tagsExclude) {
			if (contains(tag)) {
				return false
			}
		}
		return true
	}

	private fun String.toMangaTag() = MangaTag(this.toTitleCase(sourceLocale), this, source)
}
