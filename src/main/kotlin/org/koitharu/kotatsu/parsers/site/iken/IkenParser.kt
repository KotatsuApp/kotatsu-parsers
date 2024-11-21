package org.koitharu.kotatsu.parsers.site.iken

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.asTypedList
import org.koitharu.kotatsu.parsers.util.json.getBooleanOrDefault
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

internal abstract class IkenParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 18,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isMultipleTagsSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(
			MangaState.ONGOING,
			MangaState.FINISHED,
			MangaState.ABANDONED,
			MangaState.UPCOMING,
		),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHUA,
			ContentType.MANHWA,
			ContentType.OTHER,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/api/query?page=")
			append(page)
			append("&perPage=18&searchTerm=")

			filter.query?.let {
				append(filter.query.urlEncoded())
			}

			if (filter.tags.isNotEmpty()) {
				append("&genreIds=")
				filter.tags.joinTo(this, ",") { it.key }
			}

			append("&seriesType=")
			filter.types.oneOrThrowIfMany()?.let {
				append(
					when (it) {
						ContentType.MANGA -> "MANGA"
						ContentType.MANHWA -> "MANHWA"
						ContentType.MANHUA -> "MANHUA"
						ContentType.OTHER -> "RUSSIAN"
						else -> ""
					},
				)
			}

			append("&seriesStatus=")
			filter.states.oneOrThrowIfMany()?.let {
				append(
					when (it) {
						MangaState.ONGOING -> "ONGOING"
						MangaState.FINISHED -> "COMPLETED"
						MangaState.UPCOMING -> "COMING_SOON"
						MangaState.ABANDONED -> "DROPPED"
						else -> ""
					},
				)
			}
		}
		return parseMangaList(webClient.httpGet(url).parseJson())
	}

	protected open fun parseMangaList(json: JSONObject): List<Manga> {
		return json.getJSONArray("posts").mapJSON {
			val url = "/series/${it.getString("slug")}"
			Manga(
				id = it.getLong("id"),
				url = url,
				publicUrl = url.toAbsoluteUrl(domain),
				coverUrl = it.getString("featuredImage").orEmpty(),
				title = it.getString("postTitle"),
				altTitle = it.getString("alternativeTitles"),
				description = it.getString("postContent"),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = it.getString("author"),
				state = when (it.getString("seriesStatus")) {
					"ONGOING" -> MangaState.ONGOING
					"COMPLETED" -> MangaState.FINISHED
					"DROPPED", "CANCELLED" -> MangaState.ABANDONED
					"COMING_SOON" -> MangaState.UPCOMING
					else -> null
				},
				source = source,
				isNsfw = it.getBooleanOrDefault("hot", false),
			)
		}
	}


	protected open val datePattern = "yyyy-MM-dd"

	override suspend fun getDetails(manga: Manga): Manga {
		val seriesId = manga.id
		val url = "https://$domain/api/chapters?postId=$seriesId&skip=0&take=1000&order=desc&userid="
		val json = webClient.httpGet(url).parseJson().getJSONObject("post")
		val slug = json.getStringOrNull("slug")
		val data = json.getJSONArray("chapters").asTypedList<JSONObject>()
		val dateFormat = SimpleDateFormat(datePattern, Locale.ENGLISH)
		return manga.copy(
			chapters = data.mapChapters(reversed = true) { i, it ->
				val slugName = if (slug.isNullOrEmpty()) {
					it.getJSONObject("mangaPost").getString("slug")
				} else {
					slug
				}
				val chapterUrl = "/series/$slugName/${it.getString("slug")}"
				MangaChapter(
					id = it.getLong("id"),
					name = "Chapter : ${it.getInt("number")}",
					number = it.getInt("number").toFloat(),
					volume = 0,
					url = chapterUrl,
					scanlator = null,
					uploadDate = dateFormat.tryParse(it.getString("createdAt").substringBefore("T")),
					branch = null,
					source = source,
				)
			},
		)
	}

	protected open val selectPages = "main section > img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPages).map { img ->
			val url = img.requireSrc()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	protected open suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/series").parseHtml()
		return doc.selectLastOrThrow("select").select("option[value]").mapNotNullToSet {
			val key = it.attrOrNull("value") ?: return@mapNotNullToSet null
			MangaTag(
				key = key,
				title = (it.text() ?: key).toTitleCase(sourceLocale),
				source = source,
			)
		}
	}
}
