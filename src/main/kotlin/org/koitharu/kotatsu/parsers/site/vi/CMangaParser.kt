package org.koitharu.kotatsu.parsers.site.vi

import androidx.collection.ArrayMap
import androidx.collection.arraySetOf
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 20

@MangaSourceParser("CMANGA", "CManga", "vi")
internal class CMangaParser(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.CMANGA, PAGE_SIZE), MangaParserAuthProvider {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("cmangax2.com")

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
			availableTags = tags.get().values.toArraySet(),
			availableStates = arraySetOf(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED),
		)
	}

	override val authUrl: String
		get() = domain

	override suspend fun isAuthorized(): Boolean =
		context.cookieJar.getCookies(domain).any { it.name == "login_password" }

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
				.mapChapters(reversed = true) { _, jo ->
					val chapterId = jo.getLong("id_chapter")
					val info = jo.parseJson("info")
					val chapterNumber = info.getFloatOrDefault("num", -1f)
					val chapTitle = if (chapterNumber == chapterNumber.toInt().toFloat()) {
						chapterNumber.toInt().toString()
					} else {
						chapterNumber.toString()
					}
					MangaChapter(
						id = generateUid(chapterId),
						title = if (info.isLocked()) "Chương $chapTitle - Đã khoá" else "Chương $chapTitle",
						number = chapterNumber,
						volume = 0,
						url = "/album/$slug/chapter-$mangaId-$chapterId",
						uploadDate = df.tryParse(info.getString("last_update")),
						branch = null,
						scanlator = null,
						source = source,
					)
				},
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
			val id = info.getLongOrDefault("id", 0L)
			if (id == 0L) {
				return@mapJSONNotNull null
			}
			val relativeUrl = "/album/$slug-$id"
			val title = info.getString("name").replace("\\", "")
			val altTitle = info.optJSONArray("name_other")?.asTypedList<String>()?.map { it.replace("\\", "") }

			Manga(
				id = generateUid(id),
				title = title.toTitleCase(),
				altTitles = altTitle?.toSet().orEmpty(),
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
			.filterNot(::containsAdsUrl)
			.map { url ->
				MangaPage(
					id = generateUid(url),
					url = url,
					source = source,
					preview = null,
				)
			}
	}

	private suspend fun getTags(): Map<String, MangaTag> {
		val tagList = webClient.httpGet("assets/json/album_tags_image.json".toAbsoluteUrl(domain)).parseJson()
			.getJSONObject("list")
		val tags = ArrayMap<String, MangaTag>(tagList.length())
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

	private fun containsAdsUrl(url: String): Boolean = ADS_URLS.contains(url)

	private val ADS_URLS = setOf(
		"https://img.cmangapi.com/data-image/index.php?v=12&data=%7B%22ciphertext%22%3A%22jdAg8o0FDYxbm5ChST9QSeSwFrIcQi4MqqGDo2iqspxY3bbLVAQJMzjrr5WccVjTC1pNRdK0HCYNb%2B7OGXwtRFRoW5VxliHMxjgpKcLDkzl80WhSGFIxOfA2HWuGrvbx7HaB40a3o3EnKcS72lP4zpKLxu%5C%2FQIVwId%5C%2F%5C%2Fy86WeN8w%3D%22%2C%22iv%22%3A%2273f233c0af1690266e4a98daf3b57c01%22%2C%22salt%22%3A%222be54780f76bcbbd50902a3218c9a71630040238417a0192dfddedf097f329e8ccc9daf3f80604bb43326d633f56d936f98f60c0fcbbd31902e40879610299fc54b185098f729a0b1fd3a05a0122f487fe194b8e5c84315c138696db358d74829e543b2c562a5c2e74d2601f068bb5a8a7e87bfa238254466536b42abfb9c2dedb8548b075b7552b9e9e43cc5b15630ebf5e700caf7d13666f35eac6556ca05022fd27b5670429a4214fa23df9fc5d8f68833d605c34167515ae370afa6672b0471625163eea1877e569adca1e4e59757a5a395a799fd8c6f8a8569672f7878e2fffbe3c2e9a34c924c13475846ebd0a6d565181f76d24ab2b98fcc2b65f757f%22%7D",
		"https://img.cmangapi.com/data-image/index.php?v=12&data=%7B%22ciphertext%22%3A%221jRy6ZnEF6b4YNvnLg3d1outlBW%2BTfguDcREa4CSLMyfLpDBW%5C%2FBo34NAtOgyONwxm1IOMiGx1IqwXVAjiU%2BOSNnIyaH7fUxEVQFQZP97k5CDORFe7ITWyUxb149n4R9ygoelYh3FWSVn1Xy1zjsW91DH6DtY41uJXAfyapELtRQ%3D%22%2C%22iv%22%3A%22806d8fc4d9933ef3d47dcc9d6fdb5d20%22%2C%22salt%22%3A%22836ee7b216b86a3591367f17c33c086359574b034c74c810f4d46c37fece2183415009cedfe2f889a0e9710eaf4bf92d0087f9e75cc8bcabba06f1afd48408983ecf9d49b5473721a106e80834c76d2851e901c1728ccd0b13f15de6af8df1583ec24c758827c9c7f03df14b1734bb77b2924639f075c29935e96eb2b91245eb1fe93de290f7eff1a63096d716ad24e7c3d1b920c31a8fd1393f1337dd804308832152565caa496ed6e9e4c01d06182d316a2ccb08af2458e433d225d74325b4d7d3c5ec0fcd73d4e796cc5c76bfb0e88f517c4fad4451c24df65caf531d75253f580af9d4877bc0b70250177133f8454397ee8f8d41fbeb0e6ea290ed5787e5%22%7D",
        "https:\\/\\/img.cmangapi.com\\/data-image\\/index.php?v=12&data=%7B%22ciphertext%22%3A%22jdAg8o0FDYxbm5ChST9QSeSwFrIcQi4MqqGDo2iqspxY3bbLVAQJMzjrr5WccVjTC1pNRdK0HCYNb%2B7OGXwtRFRoW5VxliHMxjgpKcLDkzl80WhSGFIxOfA2HWuGrvbx7HaB40a3o3EnKcS72lP4zpKLxu%5C%2FQIVwId%5C%2F%5C%2Fy86WeN8w%3D%22%2C%22iv%22%3A%2273f233c0af1690266e4a98daf3b57c01%22%2C%22salt%22%3A%222be54780f76bcbbd50902a3218c9a71630040238417a0192dfddedf097f329e8ccc9daf3f80604bb43326d633f56d936f98f60c0fcbbd31902e40879610299fc54b185098f729a0b1fd3a05a0122f487fe194b8e5c84315c138696db358d74829e543b2c562a5c2e74d2601f068bb5a8a7e87bfa238254466536b42abfb9c2dedb8548b075b7552b9e9e43cc5b15630ebf5e700caf7d13666f35eac6556ca05022fd27b5670429a4214fa23df9fc5d8f68833d605c34167515ae370afa6672b0471625163eea1877e569adca1e4e59757a5a395a799fd8c6f8a8569672f7878e2fffbe3c2e9a34c924c13475846ebd0a6d565181f76d24ab2b98fcc2b65f757f%22%7D",
        "https:\\/\\/img.cmangapi.com\\/data-image\\/index.php?v=12&data=%7B%22ciphertext%22%3A%221jRy6ZnEF6b4YNvnLg3d1outlBW%2BTfguDcREa4CSLMyfLpDBW%5C%2FBo34NAtOgyONwxm1IOMiGx1IqwXVAjiU%2BOSNnIyaH7fUxEVQFQZP97k5CDORFe7ITWyUxb149n4R9ygoelYh3FWSVn1Xy1zjsW91DH6DtY41uJXAfyapELtRQ%3D%22%2C%22iv%22%3A%22806d8fc4d9933ef3d47dcc9d6fdb5d20%22%2C%22salt%22%3A%22836ee7b216b86a3591367f17c33c086359574b034c74c810f4d46c37fece2183415009cedfe2f889a0e9710eaf4bf92d0087f9e75cc8bcabba06f1afd48408983ecf9d49b5473721a106e80834c76d2851e901c1728ccd0b13f15de6af8df1583ec24c758827c9c7f03df14b1734bb77b2924639f075c29935e96eb2b91245eb1fe93de290f7eff1a63096d716ad24e7c3d1b920c31a8fd1393f1337dd804308832152565caa496ed6e9e4c01d06182d316a2ccb08af2458e433d225d74325b4d7d3c5ec0fcd73d4e796cc5c76bfb0e88f517c4fad4451c24df65caf531d75253f580af9d4877bc0b70250177133f8454397ee8f8d41fbeb0e6ea290ed5787e5%22%7D"
	)
}
