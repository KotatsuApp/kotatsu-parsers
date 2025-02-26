package org.koitharu.kotatsu.parsers.site.vi

import androidx.collection.ArrayMap
import androidx.collection.arraySetOf
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
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
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.getCookies
import org.koitharu.kotatsu.parsers.util.json.asTypedList
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.nullIfEmpty
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseJsonArray
import org.koitharu.kotatsu.parsers.util.parseRaw
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlBuilder
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

private const val PAGE_SIZE = 20

@MangaSourceParser("CMANGA", "CManga", "vi")
internal class CMangaParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.CMANGA, PAGE_SIZE), MangaParserAuthProvider {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("cmangax.com")

	override val availableSortOrders: Set<SortOrder>
		get() = EnumSet.of(
			SortOrder.UPDATED,
			SortOrder.NEWEST,
			SortOrder.POPULARITY,
			SortOrder.POPULARITY_TODAY,
			SortOrder.POPULARITY_WEEK,
			SortOrder.POPULARITY_MONTH,
			SortOrder.RELEVANCE,
		)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isMultipleTagsSupported = true,
			isSearchWithFiltersSupported = true,
		)

	private val tags = suspendLazy(initializer = this::getTags)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = tags.get().values.toSet(),
			availableStates = arraySetOf(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED),
		)
	}

	override val authUrl: String
		get() = domain

	override val isAuthorized: Boolean
		get() = context.cookieJar.getCookies(domain).any { it.name == "login_password" }

	override suspend fun getUsername(): String {
		val userId = webClient.httpGet("https://$domain").parseRaw()
			.substringAfter("token_user = ")
			.substringBefore(';')
			.trim()
		if (userId.isEmpty() || userId == "0") throw AuthRequiredException(
			source,
			IllegalStateException("No userId found"),
		)
		return webClient.httpGet("/api/user_info?user=$userId".toAbsoluteUrl(domain)).parseJson()
			.parseJson("info")
			.getString("name")
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val mangaId = manga.url.substringAfterLast('-')
		val slug = manga.url.substringBeforeLast('-').substringAfterLast('/')
		val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
		return manga.copy(
			chapters = webClient
				.httpGet("/api/chapter_list?album=$mangaId&page=1&limit=${Int.MAX_VALUE}&v=0v21".toAbsoluteUrl(domain))
				.parseJsonArray()
				.mapJSON { jo ->
					val chapterId = jo.getLong("id_chapter")
					val info = jo.parseJson("info")
					val chapterNumber = info.getString("num")
					MangaChapter(
						id = generateUid(chapterId),
						name = if (info.isLocked()) "Chapter $chapterNumber - locked" else "Chapter $chapterNumber",
						number = chapterNumber.toFloatOrNull()?.plus(1) ?: 0f,
						volume = 0,
						url = "/album/$slug/chapter-$mangaId-$chapterId",
						uploadDate = df.tryParse(info.getString("last_update")),
						branch = null,
						scanlator = null,
						source = source,
					)
				}.reversed(),
		)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = urlBuilder().apply {
			if (filter.query.isNullOrEmpty() && (order == SortOrder.RELEVANCE ||
					order == SortOrder.POPULARITY_TODAY ||
					order == SortOrder.POPULARITY_WEEK ||
					order == SortOrder.POPULARITY_MONTH
				)
			) {
				addPathSegments("api/home_album_top")
			} else {
				addPathSegments("api/home_album_list")
				addQueryParameter("num_chapter", "0")
				addQueryParameter("team", "0")
				addQueryParameter("sort", "update")
				addQueryParameter("tag", filter.tags.joinToString(separator = ",") { it.key })
				addQueryParameter("string", filter.query.orEmpty())
				addQueryParameter(
					"status",
					when (filter.states.oneOrThrowIfMany()) {
						MangaState.ONGOING -> "doing"
						MangaState.FINISHED -> "done"
						MangaState.PAUSED -> "drop"
						else -> "all"
					},
				)
			}

			addQueryParameter("file", "image")
			addQueryParameter("limit", PAGE_SIZE.toString())
			addQueryParameter("page", page.toString())
			addQueryParameter(
				"type",
				when (order) {
					SortOrder.UPDATED -> "update"
					SortOrder.POPULARITY -> "hot"
					SortOrder.NEWEST -> "new"
					SortOrder.POPULARITY_TODAY -> "day"
					SortOrder.POPULARITY_WEEK -> "week"
					SortOrder.POPULARITY_MONTH -> "month"
					SortOrder.RELEVANCE -> "fire" // return duplicate manga so the app won't load second page
					else -> throw IllegalArgumentException("Order not supported ${order.name}")
				},
			)
		}.build()

		val mangaList = webClient.httpGet(url).parseJson().getJSONArray("data")
		return mangaList.mapJSONNotNull { jo ->
			val info = jo.parseJson("info")
			val slug = info.getStringOrNull("url") ?: return@mapJSONNotNull null
			val id = info.optLong("id").takeIf { it != 0L } ?: return@mapJSONNotNull null
			val relativeUrl = "/album/$slug-$id"

			Manga(
				id = generateUid(id),
				title = info.optString("name").toTitleCase(),
				altTitle = info.optJSONArray("name_other")?.asTypedList<String>()?.joinToString()?.nullIfEmpty(),
				url = relativeUrl,
				publicUrl = relativeUrl.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = null,
				coverUrl = "/assets/tmp/album/${info.getString("avatar")}".toAbsoluteUrl(domain),
				tags = info.optJSONArray("tags")?.asTypedList<String>()
					?.mapNotNullToSet { tags.get()[it.lowercase()] }
					.orEmpty(),
				state = when (info.optString("status")) {
					"doing" -> MangaState.ONGOING
					"done" -> MangaState.FINISHED
					else -> null
				},
				authors = emptySet(),
				largeCoverUrl = null,
				description = info.getStringOrNull("detail")?.replace("\\\"", "\""),
				chapters = emptyList(),
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val pageResponse = webClient
			.httpGet("/api/chapter_image?chapter=${chapter.url.substringAfterLast('-')}".toAbsoluteUrl(domain))
			.parseJson()
		if (pageResponse.isLocked()) {
			throw IllegalStateException("This chapter is locked, you would need to buy it from website")
		}

		return pageResponse.getJSONArray("image")
			.asTypedList<String>()
			.map {
				MangaPage(
					id = generateUid(it),
					url = it,
					source = source,
					preview = null,
				)
			}
	}

	private suspend fun getTags(): Map<String, MangaTag> {
		val tagList = webClient.httpGet("assets/json/album_tags_image.json".toAbsoluteUrl(domain)).parseJson()
			.getJSONObject("list")
		val tags = ArrayMap<String, MangaTag>()
		for (key in tagList.keys()) {
			val jo = tagList.getJSONObject(key)
			val name = jo.getString("name")
			tags[name.lowercase()] = MangaTag(
				title = name.toTitleCase(),
				key = name,
				source = source,
			)
		}
		return tags
	}

	private fun JSONObject.parseJson(key: String): JSONObject {
		return JSONObject(getString(key))
	}

	private fun JSONObject.isLocked() = opt("lock") != null
}
