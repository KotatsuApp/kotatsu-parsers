package org.koitharu.kotatsu.parsers.site.ru

import androidx.collection.ArrayMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANGA_WTF", "MangaWtf", "ru")
internal class MangaWtfParser(
	context: MangaLoaderContext,
) : PagedMangaParser(context, MangaParserSource.MANGA_WTF, pageSize = 20) {

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(
			SortOrder.POPULARITY,
			SortOrder.RATING,
			SortOrder.UPDATED,
			SortOrder.NEWEST,
		)

	@InternalParsersApi
	override val configKeyDomain = ConfigKey.Domain("manga.wtf")

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(
			MangaState.UPCOMING,
			MangaState.PAUSED,
			MangaState.ONGOING,
			MangaState.FINISHED,
		),
		availableContentRating = EnumSet.allOf(ContentRating::class.java),
	)

	init {
		paginator.firstPage = 0
		searchPaginator.firstPage = 0
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url =
			urlBuilder("api")
				.addPathSegment("v2")
				.addPathSegment("books")
				.addQueryParameter("page", page.toString())
				.addQueryParameter("size", pageSize.toString())
				.addQueryParameter("type", "COMIC")
		when {
			filter.query.isNullOrEmpty() -> {
				url.addQueryParameter(
					"sort",
					when (order) {
						SortOrder.UPDATED -> "updatedAt,desc"
						SortOrder.POPULARITY -> "viewsCount,desc"
						SortOrder.RATING -> "likesCount,desc"
						SortOrder.NEWEST -> "createdAt,desc"
						else -> throw IllegalArgumentException("Unsupported $order")
					},
				)
				if (filter.tags.isNotEmpty()) {
					url.addQueryParameter("labelsInclude", filter.tags.joinToString(",") { it.key })
				}
				if (filter.tagsExclude.isNotEmpty()) {
					url.addQueryParameter("labelsExclude", filter.tags.joinToString(",") { it.key })
				}
				if (filter.states.isNotEmpty()) {
					url.addQueryParameter(
						"status",
						filter.states.joinToString(",") {
							when (it) {
								MangaState.ONGOING -> "ONGOING"
								MangaState.FINISHED -> "DONE"
								MangaState.ABANDONED -> ""
								MangaState.PAUSED -> "FROZEN"
								MangaState.UPCOMING -> "ANNOUNCE"
							}
						},
					)
				}
				if (filter.contentRating.isNotEmpty()) {
					url.addQueryParameter(
						"contentStatus",
						filter.contentRating.joinToString(",") {
							when (it) {
								ContentRating.SAFE -> "SAFE"
								ContentRating.SUGGESTIVE -> "UNSAFE,EROTIC"
								ContentRating.ADULT -> "PORNOGRAPHIC"
							}
						},
					)
				}
			}

			else -> {
				url.addQueryParameter("search", filter.query)
			}
		}
		val ja = webClient.httpGet(url.build()).parseJsonArray()
		return ja.mapJSON { jo -> jo.toManga() }
	}

	override suspend fun getDetails(manga: Manga): Manga =
		coroutineScope {
			val chaptersDeferred = async { getChapters(manga.url) }
			val url =
				urlBuilder("api")
					.addPathSegment("v2")
					.addPathSegment("books")
					.addPathSegment(manga.url)
			val jo = webClient.httpGet(url.build()).parseJson()
			Manga(
				id = generateUid(jo.getString("id")),
				title = jo.getJSONObject("name").getString("ru"),
				altTitle = jo.getJSONObject("name").getStringOrNull("en"),
				url = jo.getString("id"),
				publicUrl = "https://$domain/manga/${jo.getString("slug")}",
				rating = jo.getFloatOrDefault("averageRating", -10f) / 10f,
				isNsfw = jo.getStringOrNull("contentStatus").isNsfw(),
				coverUrl = jo.getString("poster"),
				tags = jo.getJSONArray("labels").mapJSONToSet { it.toMangaTag() },
				state = jo.getStringOrNull("status")?.toMangaState(),
				author =
				jo.getJSONArray("relations").asTypedList<JSONObject>().firstNotNullOfOrNull {
					if (it.getStringOrNull("type") == "AUTHOR") {
						it.getJSONObject("publisher").getStringOrNull("name")
					} else {
						null
					}
				},
				source = source,
				largeCoverUrl = null,
				description = jo.getString("description").nl2br(),
				chapters = chaptersDeferred.await(),
			)
		}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val url =
			urlBuilder("api")
				.addPathSegment("v2")
				.addPathSegment("chapters")
				.addPathSegment(chapter.url)
		val json = webClient.httpGet(url.build()).parseJson()
		return json.getJSONArray("pages").mapJSON { jo ->
			MangaPage(
				id = generateUid(jo.getString("id")),
				url = jo.getString("image"),
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val url = urlBuilder("api").addPathSegment("label")
		val json = webClient.httpGet(url.build()).parseJson()
		return json.getJSONArray("content").mapJSONToSet { jo ->
			MangaTag(
				title = jo.getJSONObject("name").getString("ru").toTitleCase(sourceLocale),
				key = jo.getString("slug"),
				source = source,
			)
		}
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val url =
			urlBuilder("api")
				.addPathSegment("book")
				.addPathSegment(seed.url)
				.addPathSegment("related")
		val ja = webClient.httpGet(url.build()).parseJsonArray()
		return ja.mapJSON { jo -> jo.toManga() }
	}

	override suspend fun getPageUrl(page: MangaPage): String = page.url

	private suspend fun getChapters(mangaId: String): List<MangaChapter> {
		val url =
			urlBuilder("api")
				.addPathSegment("v2")
				.addPathSegment("chapters")
				.addQueryParameter("bookId", mangaId)
		val ja = webClient.httpGet(url.build()).parseJsonArray()
		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT)
		val branches = ArrayMap<String, String>()
		return ja
			.mapJSON { jo ->
				val number = jo.getFloatOrDefault("number", 0f)
				val volume = jo.getIntOrDefault("volume", 0)
				val branchId = jo.getString("branchId")
				MangaChapter(
					id = generateUid(jo.getString("id")),
					name =
					jo.getStringOrNull("name") ?: buildString {
						if (volume > 0) append("Том ").append(volume).append(' ')
						if (number > 0) append("Глава ").append(number) else append("Без имени")
					},
					number = number,
					volume = volume,
					url = jo.getString("id"),
					scanlator = null,
					uploadDate = dateFormat.tryParse(jo.getString("createdAt")),
					branch = branches.getOrPut(branchId) { getBranchName(branchId) },
					source = source,
				)
			}.reversed()
	}

	private suspend fun getBranchName(id: String): String? =
		runCatchingCancellable {
			val url =
				urlBuilder("api")
					.addPathSegment("branch")
					.addPathSegment(id)
			val json = webClient.httpGet(url.build()).parseJson()
			json.getJSONArray("publishers").mapJSONToSet { it.getStringOrNull("name") }.firstOrNull()
		}.getOrElse {
			id.substringBefore('-')
		}

	private fun String.toMangaState() =
		when (this.uppercase(Locale.ROOT)) {
			"DONE" -> MangaState.FINISHED
			"ONGOING" -> MangaState.ONGOING
			"FROZEN" -> MangaState.PAUSED
			"ANNOUNCE" -> MangaState.UPCOMING
			else -> null
		}

	private fun String?.isNsfw() =
		this.equals("EROTIC", ignoreCase = true) ||
			this.equals("PORNOGRAPHIC", ignoreCase = true)

	private fun JSONObject.toMangaTag() =
		MangaTag(
			title = getString("name").toTitleCase(sourceLocale),
			key = getString("slug"),
			source = source,
		)

	private fun JSONObject.toManga() =
		Manga(
			id = generateUid(getString("id")),
			title = getJSONObject("name").getString("ru"),
			altTitle = getJSONObject("name").getStringOrNull("en"),
			url = getString("id"),
			publicUrl = "https://$domain/manga/${getString("slug")}",
			rating = getFloatOrDefault("averageRating", -10f) / 10f,
			isNsfw = getStringOrNull("contentStatus").isNsfw(),
			coverUrl = getString("poster"),
			tags = setOf(),
			state = getStringOrNull("status")?.toMangaState(),
			author = null,
			source = source,
		)
}
