package org.koitharu.kotatsu.parsers.site.vi

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import org.jsoup.HttpStatusException
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("CUUTRUYEN", "CuuTruyen", "vi")
internal class CuuTruyenParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.CUUTRUYEN, 20), Interceptor {

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.KOTATSU)

	override val configKeyDomain = ConfigKey.Domain(
		"cuutruyen.net",
		"nettrom.com",
		"hetcuutruyen.net",
		"cuutruyent9sv7.xyz",
	)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/api/v2/mangas/search?q=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				else -> {
					val tag = filter.tags.oneOrThrowIfMany()
					if (tag != null) {
						append("/api/v2/tags/")
						append(tag.key)
					} else {
						append("/api/v2/mangas")
						when (order) {
							SortOrder.UPDATED -> append("/recently_updated")
							SortOrder.POPULARITY -> append("/top")
							SortOrder.NEWEST -> append("/recently_updated")
							else -> append("/recently_updated")
						}
					}
					append("?page=")
					append(page.toString())
				}
			}

			append("&per_page=")
			append(pageSize)
		}

		val json = try {
			webClient.httpGet(url).parseJson()
		} catch (e: HttpStatusException) {
			if (e.statusCode == HttpURLConnection.HTTP_INTERNAL_ERROR) {
				return emptyList()
			} else {
				throw e
			}
		}
		val data = json.getJSONArray("data")

		return data.mapJSON { jo ->
			Manga(
				id = generateUid(jo.getLong("id")),
				url = "/api/v2/mangas/${jo.getLong("id")}",
				publicUrl = "https://$domain/manga/${jo.getLong("id")}",
				title = jo.getString("name"),
				altTitle = null,
				coverUrl = jo.getString("cover_mobile_url"),
				largeCoverUrl = jo.getString("cover_url"),
				author = jo.getStringOrNull("author_name"),
				tags = emptySet(),
				state = null,
				description = null,
				isNsfw = isNsfwSource,
				source = source,
				rating = RATING_UNKNOWN,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val url = "https://" + domain + manga.url
		val chapters = async {
			webClient.httpGet("$url/chapters").parseJson().getJSONArray("data")
		}
		val json = webClient.httpGet(url).parseJson().getJSONObject("data")
		val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

		manga.copy(
			title = json.getStringOrNull("name") ?: manga.title,
			isNsfw = json.getBooleanOrDefault("is_nsfw", manga.isNsfw),
			author = json.optJSONObject("author")?.getStringOrNull("name")?.substringBefore(','),
			description = json.getString("full_description"),
			tags = json.optJSONArray("tags")?.mapJSONToSet { jo ->
				MangaTag(
					title = jo.getString("name").toTitleCase(sourceLocale),
					key = jo.getString("slug"),
					source = source,
				)
			}.orEmpty(),
			chapters = chapters.await().mapJSON { jo ->
				val chapterId = jo.getLong("id")
				val number = jo.getFloatOrDefault("number", 0f)
				MangaChapter(
					id = generateUid(chapterId),
					name = jo.getStringOrNull("name") ?: number.formatSimple(),
					number = number,
					volume = 0,
					url = "/api/v2/chapters/$chapterId",
					scanlator = jo.optString("group_name"),
					uploadDate = chapterDateFormat.tryParse(jo.getStringOrNull("created_at")),
					branch = null,
					source = source,
				)
			}.reversed(),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val url = "https://$domain${chapter.url}"
		val json = webClient.httpGet(url).parseJson().getJSONObject("data")

		return json.getJSONArray("pages").mapJSON { jo ->
			val imageUrl = jo.getString("image_url").toHttpUrl().newBuilder()
			val id = jo.getLong("id")
			val drm = jo.getStringOrNull("drm_data")
			if (!drm.isNullOrEmpty()) {
				imageUrl.fragment(DRM_DATA_KEY + drm)
			}
			MangaPage(
				id = generateUid(id),
				url = imageUrl.build().toString(),
				preview = null,
				source = source,
			)
		}
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val response = chain.proceed(chain.request())
		val fragment = response.request.url.fragment

		if (fragment == null || !fragment.contains(DRM_DATA_KEY)) {
			return response
		}

		val drmData = fragment.substringAfter(DRM_DATA_KEY)
		return context.redrawImageResponse(response) { bitmap ->
			unscrambleImage(bitmap, drmData)
		}
	}

	private fun unscrambleImage(bitmap: Bitmap, drmData: String): Bitmap {
		val data = context.decodeBase64(drmData)
			.decodeXorCipher(DECRYPTION_KEY)
			.toString(Charsets.UTF_8)

		if (!data.startsWith("#v4|")) {
			throw IOException("Invalid DRM data (does not start with expected magic bytes): $data")
		}

		val result = context.createBitmap(bitmap.width, bitmap.height)
		var sy = 0
		for (t in data.split('|').drop(1)) {
			val (dy, height) = t.split('-').map(String::toInt)
			val srcRect = Rect(0, sy, bitmap.width, sy + height)
			val dstRect = Rect(0, dy, bitmap.width, dy + height)

			result.drawBitmap(bitmap, srcRect, dstRect)
			sy += height
		}

		return result
	}

	private fun ByteArray.decodeXorCipher(key: String): ByteArray {
		val k = key.toByteArray(Charsets.UTF_8)

		return this.mapIndexed { i, b ->
			(b.toInt() xor k[i % k.size].toInt()).toByte()
		}.toByteArray()
	}

	private companion object {
		const val DRM_DATA_KEY = "drm_data="
		const val DECRYPTION_KEY = "3141592653589793"
	}
}
