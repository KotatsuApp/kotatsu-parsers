package org.koitharu.kotatsu.parsers.site.en

import androidx.collection.ArrayMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.asTypedList
import org.koitharu.kotatsu.parsers.util.json.toJSONArrayOrNull
import org.koitharu.kotatsu.parsers.util.json.toJSONObjectOrNull
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("ASURASCANS", "AsuraComic", "en")
internal class AsuraScansParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.ASURASCANS, pageSize = 30) {

	override val configKeyDomain = ConfigKey.Domain("asuracomic.net")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.RATING,
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL_DESC,
		SortOrder.ALPHABETICAL,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = getOrCreateTagMap().values.toSet(),
		availableStates = EnumSet.of(
			MangaState.ONGOING,
			MangaState.FINISHED,
			MangaState.ABANDONED,
			MangaState.PAUSED,
			MangaState.UPCOMING,
		),
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
			append("/series?page=")
			append(page)

			filter.query?.let {
				append("&name=")
				append(filter.query.urlEncoded())
			}

			if (filter.tags.isNotEmpty()) {
				append("&genres=")
				append(filter.tags.joinToString(separator = ",") { it.key })
			}

			filter.states.oneOrThrowIfMany()?.let {
				append("&status=")
				append(
					when (it) {
						MangaState.ONGOING -> "1"
						MangaState.FINISHED -> "3"
						MangaState.ABANDONED -> "4"
						MangaState.PAUSED -> "2"
						MangaState.UPCOMING -> "6"
						else -> throw IllegalArgumentException("$it not supported")
					},
				)
			}

			filter.types.oneOrThrowIfMany()?.let {
				append("&types=")
				append(
					when (it) {
						ContentType.MANGA -> "3"
						ContentType.MANHWA -> "1"
						ContentType.MANHUA -> "2"
						else -> ""
					},
				)
			}

			append("&order=")
			when (order) {
				SortOrder.RATING -> append("rating")
				SortOrder.UPDATED -> append("update")
				SortOrder.POPULARITY -> append("bookmarks")
				SortOrder.ALPHABETICAL_DESC -> append("desc")
				SortOrder.ALPHABETICAL -> append("asc")
				else -> append("update")
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.grid > a[href]").map { a ->
			val href = "/" + a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = a.selectFirst("img")?.src(),
				title = a.selectFirst("div.block > span.block")?.text().orEmpty(),
				altTitles = emptySet(),
				rating = a.selectFirst("div.block  label.ml-1")?.text()?.toFloatOrNull()?.div(10f) ?: RATING_UNKNOWN,
				tags = emptySet(),
				authors = emptySet(),
				state = when (a.selectLast("span.status")?.text()) {
					"Ongoing" -> MangaState.ONGOING
					"Completed" -> MangaState.FINISHED
					"Hiatus" -> MangaState.PAUSED
					"Dropped" -> MangaState.ABANDONED
					"Coming Soon" -> MangaState.UPCOMING
					else -> null
				},
				source = source,
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
			)
		}
	}

	private var tagCache: ArrayMap<String, MangaTag>? = null
	private val mutex = Mutex()

	private suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val tagMap = ArrayMap<String, MangaTag>()
		val json =
			webClient.httpGet("https://gg.$domain/api/series/filters").parseJson().getJSONArray("genres")
				.asTypedList<JSONObject>()
		for (el in json) {
			if (el.getString("name").isEmpty()) continue
			tagMap[el.getString("name")] = MangaTag(
				key = el.getInt("id").toString(),
				title = el.getString("name"),
				source = source,
			)
		}
		tagCache = tagMap
		tagMap
	}

	private val regexDate = """(\d+)(st|nd|rd|th)""".toRegex()

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val tagMap = getOrCreateTagMap()
		val selectTag = doc.select("div[class^=space] > div.flex > button.text-white")
		val tags = selectTag.mapNotNullToSet { tagMap[it.text()] }
		val author = doc.selectFirst("div.grid > div:has(h3:eq(0):containsOwn(Author)) > h3:eq(1)")?.text().orEmpty()
		return manga.copy(
			description = doc.selectFirst("span.font-medium.text-sm")?.text().orEmpty(),
			tags = tags,
			authors = setOf(author),
			chapters = doc.select("div.scrollbar-thumb-themecolor > div.group").mapChapters(reversed = true) { i, div ->
				val a = div.selectLastOrThrow("a")
				val urlRelative = "/series/" + a.attrAsRelativeUrl("href")
				val url = urlRelative.toAbsoluteUrl(domain)
				val date = div.selectLast("h3")?.text().orEmpty()
				val cleanDate = date.replace(regexDate, "$1")
				MangaChapter(
					id = generateUid(url),
					title = div.selectFirst("h3")?.textOrNull(),
					number = i + 1f,
					volume = 0,
					url = url,
					scanlator = null,
					uploadDate = SimpleDateFormat("MMMM d yyyy", Locale.US)
						.parseSafe(cleanDate),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val data = doc.selectOrThrow("script").mapNotNull { x ->
			x.data().substringBetween("self.__next_f.push(", ")", "")
				.trim()
				.nullIfEmpty()
		}.flatMap { it.jsonStrings() }
			.joinToString("")
			.split('\n')
			.mapNotNull { x ->
				x.substringAfter(':').toJSONObjectOrNull()
			}
		val pages = data.filter { it.has("order") && it.has("url") }
			.associate { it.getInt("order") to it.getString("url") }.values
		return pages.map { url ->
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private fun String.jsonStrings(): List<String> {
		val ja = toJSONArrayOrNull() ?: return emptyList()
		val result = ArrayList<String>(ja.length())
		repeat(ja.length()) { i ->
			(ja.get(i) as? String)?.let { item ->
				result.add(item)
			}
		}
		return result
	}
}
