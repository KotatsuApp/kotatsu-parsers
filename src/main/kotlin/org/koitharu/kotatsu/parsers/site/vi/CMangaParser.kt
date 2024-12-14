package org.koitharu.kotatsu.parsers.site.vi

import androidx.collection.ArrayMap
import androidx.collection.ArraySet
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
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseJsonArray
import org.koitharu.kotatsu.parsers.util.parseRaw
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlBuilder
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.EnumSet
import java.util.Locale

private const val PAGE_SIZE = 50

@MangaSourceParser("CMANGA", "CManga", "vi")
internal class CMangaParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.CMANGA, PAGE_SIZE), MangaParserAuthProvider {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("cmangal.com")

	override val availableSortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.NEWEST)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(isSearchSupported = true)

	private val tags = suspendLazy(initializer = this::getTags)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = tags.get().values.toSet(),
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
					val chapterNumber = info.getInt("num")
					MangaChapter(
						id = generateUid(chapterId),
						name = if (info.isLocked()) "Chapter $chapterNumber - locked" else "Chapter $chapterNumber",
						number = chapterNumber + 1f,
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
		val mangaList = if (filter.query.isNullOrEmpty()) {
			val url = urlBuilder()
				.addPathSegments("api/home_album_list")
				.addQueryParameter("num_chapter", "0")
				.addQueryParameter("sort", "update")
				.addQueryParameter(
					"type",
					when (order) {
						SortOrder.UPDATED -> "new"
						SortOrder.POPULARITY -> "trending"
						SortOrder.NEWEST -> "hot"
						else -> throw IllegalArgumentException("Order not supported ${order.name}")
					},
				)
				.addQueryParameter("tag", if (filter.tags.isEmpty()) "all" else filter.tags.first().key)
				.addQueryParameter("limit", PAGE_SIZE.toString())
				.addQueryParameter("page", page.toString())
				.build()
			webClient.httpGet(url).parseJson().getJSONArray("data")
		} else {
			if (page > 1) {
				return emptyList()
			}

			val url = urlBuilder()
				.addPathSegments("api/search")
				.addQueryParameter("child_protect", "off")
				.addQueryParameter("string", filter.query)
				.build()
			webClient.httpGet(url).parseJsonArray()
		}

		return mangaList.mapJSON { jo ->
			val info = jo.parseJson("info")
			val slug = info.getString("url")
			val id = info.getLong("id")
			val relativeUrl = "/album/$slug-$id"
			val mangaTags = ArraySet<MangaTag>()
			info.getJSONArray("tags").asTypedList<String>().forEach {
				tags.get()[it.lowercase()]?.let { mangaTags.add(it) }
			}

			Manga(
				id = generateUid(id),
				title = info.getString("name").toTitleCase(),
				altTitle = info.getJSONArray("name_other").asTypedList<String>().joinToString(),
				url = relativeUrl,
				publicUrl = relativeUrl.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = "/assets/tmp/album/${info.getString("avatar")}".toAbsoluteUrl(domain),
				tags = mangaTags,
				state = when (info.getString("status")) {
					"doing" -> MangaState.ONGOING
					else -> null // can't find any manga with other status than on going
				},
				author = null,
				largeCoverUrl = null,
				description = info.optString("detail"),
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
		val tagsResponse = webClient.httpGet("api/data?data=album_tags".toAbsoluteUrl(domain)).parseJson()
		val tags = ArrayMap<String, MangaTag>()
		for (key in tagsResponse.keys()) {
			val jo = tagsResponse.getJSONObject(key)
			val title = jo.getString("name")
			tags[title.lowercase()] = MangaTag(
				title = title,
				key = jo.getString("url"),
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
