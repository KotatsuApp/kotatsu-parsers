package org.koitharu.kotatsu.parsers.site.vi

import androidx.collection.arraySetOf
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
		"cuutruyenpip7z.site",
		"cuutruyen5c844.site",
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = availableTags(),
		)
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
		val data = json.optJSONArray("data") ?: json.getJSONObject("data").getJSONArray("mangas")

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

		return context.redrawImageResponse(response) { bitmap ->
			val drmData = fragment.substringAfter(DRM_DATA_KEY)
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

	private fun availableTags() = arraySetOf(
		MangaTag("Manga", "manga", source),
		MangaTag("Đang tiến hành", "dang-tien-hanh", source),
		MangaTag("Thể thao", "the-thao", source),
		MangaTag("Hài hước", "hai-huoc", source),
		MangaTag("Shounen", "shounen", source),
		MangaTag("Học đường", "hoc-duong", source),
		MangaTag("Chất lượng cao", "chat-luong-cao", source),
		MangaTag("Comedy", "comedy", source),
		MangaTag("Action", "action", source),
		MangaTag("Horror", "horror", source),
		MangaTag("Sci-fi", "sci-fi", source),
		MangaTag("Aliens", "aliens", source),
		MangaTag("Martial Arts", "martial-arts", source),
		MangaTag("Military", "military", source),
		MangaTag("Monsters", "monsters", source),
		MangaTag("Supernatural", "supernatural", source),
		MangaTag("Web Comic", "web-comic", source),
		MangaTag("Phiêu lưu", "phieu-luu", source),
		MangaTag("Hậu tận thế", "hau-tan-the", source),
		MangaTag("Hành động", "hanh-dong", source),
		MangaTag("Đã hoàn thành", "da-hoan-thanh", source),
		MangaTag("Sinh tồn", "sinh-ton", source),
		MangaTag("Du hành thời gian", "du-hanh-thoi-gian", source),
		MangaTag("Khoa học", "khoa-hoc", source),
		MangaTag("Tạm ngưng", "tam-ngung", source),
		MangaTag("NSFW", "nsfw", source),
		MangaTag("Bạo lực", "bao-luc", source),
		MangaTag("Khoả thân", "khoa-than", source),
		MangaTag("Bí ẩn", "bi-an", source),
		MangaTag("Trinh thám", "trinh-tham", source),
		MangaTag("Kinh dị", "kinh-di", source),
		MangaTag("Máu me", "mau-me", source),
		MangaTag("Tình dục", "tinh-duc", source),
		MangaTag("Có màu", "co-mau", source),
		MangaTag("Manhwa", "manhwa", source),
		MangaTag("Webtoon", "webtoon", source),
		MangaTag("Siêu nhiên", "sieu-nhien", source),
		MangaTag("Fantasy", "fantasy", source),
		MangaTag("Võ thuật", "vo-thuat", source),
		MangaTag("Drama", "drama", source),
		MangaTag("Hệ thống", "he-thong", source),
		MangaTag("Lãng mạn", "lang-man", source),
		MangaTag("Đời thường", "doi-thuong", source),
		MangaTag("Công sở", "cong-so", source),
		MangaTag("Sát thủ", "sat-thu", source),
		MangaTag("Phép thuật", "phep-thuat", source),
		MangaTag("Tội phạm", "toi-pham", source),
		MangaTag("Seinen", "seinen", source),
		MangaTag("Isekai", "isekai", source),
		MangaTag("Chuyển sinh", "chuyen-sinh", source),
		MangaTag("Harem", "harem", source),
		MangaTag("Mecha", "mecha", source),
		MangaTag("Trung cổ", "trung-co", source),
		MangaTag("LGBT", "lgbt", source),
		MangaTag("Yaoi", "yaoi", source),
		MangaTag("Game", "game", source),
		MangaTag("Bi kịch", "bi-kich", source),
		MangaTag("Động vật", "dong-vat", source),
		MangaTag("Tâm lý", "tam-ly", source),
		MangaTag("Manhua", "manhua", source),
		MangaTag("Nam biến nữ", "nam-bien-nu", source),
		MangaTag("Romcom", "romcom", source),
		MangaTag("Award Winning", "award-winning", source),
		MangaTag("Oneshot", "oneshot", source),
		MangaTag("Khoa học viễn tưởng", "khoa-hoc-vien-tuong", source),
		MangaTag("Dark Fantasy", "dark-fantasy", source),
		MangaTag("Zombie", "zombie", source),
		MangaTag("Nam x Nam", "nam-x-nam", source),
		MangaTag("Giật gân", "giat-gan", source),
		MangaTag("Cảnh sát", "canh-sat", source),
		MangaTag("NTR", "ntr", source),
		MangaTag("Cooking", "cooking", source),
		MangaTag("Ẩm thực", "am-thuc", source),
		MangaTag("Ecchi", "ecchi", source),
		MangaTag("Quái vật", "quai-vat", source),
		MangaTag("Vampires", "vampires", source),
		MangaTag("Nam giả nữ", "nam-gia-nu", source),
		MangaTag("Yakuza", "yakuza", source),
		MangaTag("Romance", "romance", source),
		MangaTag("Sport", "sport", source),
		MangaTag("Shoujo", "shoujo", source),
		MangaTag("Ninja", "ninja", source),
		MangaTag("Lịch sử", "lich-su", source),
		MangaTag("Doujinshi", "doujinshi", source),
		MangaTag("Databook", "databook", source),
		MangaTag("Adventure", "adventure", source),
		MangaTag("Y học", "y-hoc", source),
		MangaTag("Miễn bản quyền", "mien-ban-quyen", source),
		MangaTag("Josei", "josei", source),
		MangaTag("Psychological", "psychological", source),
		MangaTag("Anime", "anime", source),
		MangaTag("Yuri", "yuri", source),
		MangaTag("Yonkoma", "yonkoma", source),
		MangaTag("Quân đội", "quan-doi", source),
		MangaTag("Nữ giả nam", "nu-gia-nam", source),
		MangaTag("Chính trị", "chinh-tri", source),
		MangaTag("Tuyển tập", "tuyen-tap", source),
		MangaTag("Tu tiên", "tu-tien", source),
		MangaTag("Vô CP", "vo-cp", source),
		MangaTag("Xuyên không", "xuyen-khong", source),
		MangaTag("Việt Nam", "viet-nam", source),
		MangaTag("Toán học", "toan-hoc", source),
		MangaTag("Thiếu niên", "thieu-nien", source),
		MangaTag("Tình yêu", "tinh-yeu", source),
		MangaTag("Chính kịch", "chinh-kich", source),
		MangaTag("Ngọt ngào", "ngot-ngao", source),
		MangaTag("Wholesome", "wholesome", source),
		MangaTag("Smut", "smut", source),
		MangaTag("Gore", "gore", source),
		MangaTag("School Life", "school-life", source),
		MangaTag("Slice of Life", "slice-of-life", source),
		MangaTag("Tragedy", "tragedy", source),
		MangaTag("Mystery", "mystery", source),
		MangaTag("Atlus", "atlus", source),
		MangaTag("Sega", "sega", source),
		MangaTag("RPG", "rpg", source),
		MangaTag("Chuyển thể", "chuyen-the", source),
		MangaTag("Historical", "historical", source),
		MangaTag("Medical", "medical", source),
		MangaTag("Ghosts", "ghosts", source),
		MangaTag("Thriller", "thriller", source),
		MangaTag("Animals", "animals", source),
		MangaTag("Survival", "survival", source),
		MangaTag("Samurai", "samurai", source),
		MangaTag("Virtual Reality", "virtual-reality", source),
		MangaTag("Video Games", "video-games", source),
		MangaTag("Monster Girls", "monster-girls", source),
		MangaTag("Adaption", "adaption", source),
		MangaTag("Idol", "idol", source),
	)

	private companion object {
		const val DRM_DATA_KEY = "drm_data="
		const val DECRYPTION_KEY = "3141592653589793"
	}
}
