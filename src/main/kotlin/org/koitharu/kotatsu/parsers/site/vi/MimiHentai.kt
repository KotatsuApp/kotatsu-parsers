package org.koitharu.kotatsu.parsers.site.vi

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MIMIHENTAI", "MimiHentai", "vi", type = ContentType.HENTAI)
internal class MimiHentai(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.MIMIHENTAI, 18) {

	private val apiSuffix = "api/v1/manga"
	override val configKeyDomain = ConfigKey.Domain("mimihentai.com", "hentaihvn.com")
	override val userAgentKey = ConfigKey.UserAgent(UserAgents.KOTATSU)

	override suspend fun getFavicons(): Favicons {
		return Favicons(
			listOf(
				Favicon(
					"https://raw.githubusercontent.com/dragonx943/plugin-sdk/refs/heads/sources/mimihentai/app/src/main/ic_launcher-playstore.png",
					512,
					null),
			),
			domain,
		)
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.remove(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.ALPHABETICAL,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_TODAY,
		SortOrder.POPULARITY_WEEK,
		SortOrder.POPULARITY_MONTH,
		SortOrder.RATING,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isMultipleTagsSupported = true,
			isAuthorSearchSupported = true,
			isTagsExclusionSupported = true,
		)

	init {
		setFirstPage(0)
	}

	override suspend fun getFilterOptions() = MangaListFilterOptions(availableTags = fetchTags())

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append("$domain/$apiSuffix")

			if (!filter.query.isNullOrEmpty() ||
				!filter.author.isNullOrEmpty() ||
				filter.tags.isNotEmpty()
			) {
				append("/advance-search?page=")
				append(page)
				append("&max=18") // page size, avoid rate limit

				if (!filter.query.isNullOrEmpty()) {
					append("&name=")
					append(filter.query.urlEncoded())
				}

				if (!filter.author.isNullOrEmpty()) {
					append("&author=")
					append(filter.author.urlEncoded())
				}

				if (filter.tags.isNotEmpty()) {
					append("&genre=")
					append(filter.tags.joinToString(",") { it.key })
				}

				if (filter.tagsExclude.isNotEmpty()) {
					append("&ex=")
					append(filter.tagsExclude.joinToString(",") { it.key })
				}

				append("&sort=")
				append(
					when (order) {
						SortOrder.UPDATED -> "updated_at"
						SortOrder.ALPHABETICAL -> "title"
						SortOrder.POPULARITY -> "follows"
						SortOrder.POPULARITY_TODAY,
						SortOrder.POPULARITY_WEEK,
						SortOrder.POPULARITY_MONTH -> "views"
						SortOrder.RATING -> "likes"
						else -> ""
					}
				)
			}

			else {
				append(
					when (order) {
						SortOrder.UPDATED -> "/tatcatruyen?page=$page&sort=updated_at"
						SortOrder.ALPHABETICAL -> "/tatcatruyen?page=$page&sort=title"
						SortOrder.POPULARITY -> "/tatcatruyen?page=$page&sort=follows"
						SortOrder.POPULARITY_TODAY -> "/tatcatruyen?page=$page&sort=views"
						SortOrder.POPULARITY_WEEK -> "/top-manga?page=$page&timeType=1&limit=18"
						SortOrder.POPULARITY_MONTH -> "/top-manga?page=$page&timeType=2&limit=18"
						SortOrder.RATING -> "/tatcatruyen?page=$page&sort=likes"
						else -> "/tatcatruyen?page=$page&sort=updated_at" // default
					}
				)

				if (filter.tagsExclude.isNotEmpty()) {
					append("&ex=")
					append(filter.tagsExclude.joinToString(",") { it.key })
				}
			}
		}

		val raw = webClient.httpGet(url)
		return if (url.contains("/top-manga")) {
			val data = raw.parseJsonArray()
			parseTopMangaList(data)
		} else {
			val data = raw.parseJson().getJSONArray("data")
			parseMangaList(data)
		}
	}

	private fun parseTopMangaList(data: JSONArray): List<Manga> {
		return data.mapJSON { jo ->
			val id = jo.getLong("id")
			val title = jo.getString("title").takeIf { it.isNotEmpty() } ?: "Web chưa đặt tên"
			val description = jo.getStringOrNull("description")

			val differentNames = mutableSetOf<String>().apply {
				jo.optJSONArray("differentNames")?.let { namesArray ->
					for (i in 0 until namesArray.length()) {
						namesArray.optString(i)?.takeIf { it.isNotEmpty() }?.let { name ->
							add(name)
						}
					}
				}
			}

			val authors = jo.optJSONArray("authors")?.mapJSON {
				it.getString("name")
			}?.toSet() ?: emptySet()

			val tags = jo.optJSONArray("genres")?.mapJSON { genre ->
				MangaTag(
					key = genre.getLong("id").toString(),
					title = genre.getString("name"),
					source = source
				)
			}?.toSet() ?: emptySet()

			Manga(
				id = generateUid(id),
				title = title,
				altTitles = differentNames,
				url = "/$apiSuffix/info/$id",
				publicUrl = "https://$domain/g/$id",
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = jo.getString("coverUrl"),
				state = null,
				description = description,
				tags = tags,
				authors = authors,
				source = source,
			)
		}
	}

	private fun parseMangaList(data: JSONArray): List<Manga> {
		return data.mapJSON { jo ->
			val id = jo.getLong("id")
			val title = jo.getString("title").takeIf { it.isNotEmpty() } ?: "Web chưa đặt tên"
			val description = jo.getStringOrNull("description")

			val differentNames = mutableSetOf<String>().apply {
				jo.optJSONArray("differentNames")?.let { namesArray ->
					for (i in 0 until namesArray.length()) {
						namesArray.optString(i)?.takeIf { it.isNotEmpty() }?.let { name ->
							add(name)
						}
					}
				}
			}

			val authors = jo.getJSONArray("authors").mapJSON {
				it.getString("name")
			}.toSet()

			val tags = jo.getJSONArray("genres").mapJSON { genre ->
				MangaTag(
					key = genre.getLong("id").toString(),
					title = genre.getString("name"),
					source = source
				)
			}.toSet()

			Manga(
				id = generateUid(id),
				title = title,
				altTitles = differentNames,
				url = "/$apiSuffix/info/$id",
				publicUrl = "https://$domain/g/$id",
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = jo.getString("coverUrl"),
				state = null,
				tags = tags,
				description = description,
				authors = authors,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = manga.url.toAbsoluteUrl(domain)
		val json = webClient.httpGet(url).parseJson()
		val id = json.getLong("id")
		val description = json.getStringOrNull("description")
		val uploaderName = json.getJSONObject("uploader").getString("displayName")

		val tags = json.getJSONArray("genres").mapJSONToSet { jo ->
			MangaTag(
				title = jo.getString("name").toTitleCase(sourceLocale),
				key = jo.getLong("id").toString(),
				source = source,
			)
		}

		val urlChaps = "https://$domain/$apiSuffix/gallery/$id"
		val parsedChapters = webClient.httpGet(urlChaps).parseJsonArray()
		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US)
		val chapters = parsedChapters.mapJSON { jo ->
			MangaChapter(
				id = generateUid(jo.getLong("id")),
				title = jo.getStringOrNull("title"),
				number = jo.getFloatOrDefault("order", 0f),
				url = "${jo.getLong("id")}",
				uploadDate = dateFormat.parse(jo.getString("createdAt"))?.time ?: 0L,
				source = source,
				scanlator = uploaderName,
				branch = null,
				volume = 0,
			)
		}.reversed()

		return manga.copy(
			tags = tags,
			description = description,
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val url = context.decodeBase64(KuroNeko.PATH)
			.decodeXorCipher()
			.toString(Charsets.UTF_8) + "/" + chapter.url
		val json = webClient.httpGet(url).parseJson()
		return json.getJSONArray("pages").mapJSON { jo ->
			val imageUrl = jo.getString("imageUrl")
			val gt = jo.getStringOrNull("drm")
			MangaPage(
				id = generateUid(imageUrl),
				url = if (gt != null) "$imageUrl#$GT$gt" else imageUrl,
				preview = null,
				source = source,
			)
		}
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val response = chain.proceed(chain.request())
		val fragment = response.request.url.fragment

		if (fragment == null || !fragment.contains(GT)) {
			return response
		}

		return context.redrawImageResponse(response) { bitmap ->
			val gt = fragment.substringAfter(GT)
			runBlocking {
				extractMetadata(bitmap, gt)
			}
		}
	}

	private fun extractMetadata(bitmap: Bitmap, gt: String): Bitmap {
		val metadata = JSONObject().apply {
			var sw = 0
			var sh = 0
			val pos = JSONObject()
			val dims = JSONObject()

			for (t in gt.split("|")) {
				when {
					t.startsWith("sw:") -> sw = t.substring(3).toInt()
					t.startsWith("sh:") -> sh = t.substring(3).toInt()
					t.contains("@") && t.contains(">") -> {
						val (left, right) = t.split(">")
						val (n, rectStr) = left.split("@")
						val (x, y, w, h) = rectStr.split(",").map { it.toInt() }
						dims.put(n, JSONObject().apply {
							put("x", x)
							put("y", y)
							put("width", w)
							put("height", h)
						})
						pos.put(n, right)
					}
				}
			}
			put("sw", sw)
			put("sh", sh)
			put("dims", dims)
			put("pos", pos)
		}

		val sw = metadata.optInt("sw")
		val sh = metadata.optInt("sh")
		if (sw <= 0 || sh <= 0) return bitmap

		val fullW = bitmap.width
		val fullH = bitmap.height

		val working = context.createBitmap(sw, sh).also { k ->
			k.drawBitmap(bitmap, Rect(0, 0, sw, sh), Rect(0, 0, sw, sh))
		}

		val keys = arrayOf("00","01","02","10","11","12","20","21","22")
		val baseW = sw / 3
		val baseH = sh / 3
		val rw = sw % 3
		val rh = sh % 3
		val defaultDims = HashMap<String, IntArray>().apply {
			for (k in keys) {
				val i = k[0].digitToInt()
				val j = k[1].digitToInt()
				val w = baseW + if (j == 2) rw else 0
				val h = baseH + if (i == 2) rh else 0
				put(k, intArrayOf(j * baseW, i * baseH, w, h))
			}
		}

		val dimsJson = metadata.optJSONObject("dims") ?: JSONObject()
		val dims = HashMap<String, IntArray>().apply {
			for (k in keys) {
				val jo = dimsJson.optJSONObject(k)
				if (jo != null) {
					put(k, intArrayOf(
						jo.getInt("x"),
						jo.getInt("y"),
						jo.getInt("width"),
						jo.getInt("height"),
					))
				} else {
					put(k, defaultDims.getValue(k))
				}
			}
		}

		val pos = metadata.optJSONObject("pos") ?: JSONObject()
		val inv = HashMap<String, String>().apply {
			val it = pos.keys()
			while (it.hasNext()) {
				val a = it.next()
				val b = pos.getString(a)
				put(b, a)
			}
		}

		val result = context.createBitmap(fullW, fullH)

		for (k in keys) {
			val srcKey = inv[k] ?: continue
			val s = dims.getValue(k)
			val d = dims.getValue(srcKey)
			result.drawBitmap(
				working,
				Rect(s[0], s[1], s[0] + s[2], s[1] + s[3]),
				Rect(d[0], d[1], d[0] + d[2], d[1] + d[3]),
			)
		}

		if (sh < fullH) {
			result.drawBitmap(
				bitmap,
				Rect(0, sh, fullW, fullH),
				Rect(0, sh, fullW, fullH),
			)
		}
		if (sw < fullW) {
			result.drawBitmap(
				bitmap,
				Rect(sw, 0, fullW, sh),
				Rect(sw, 0, fullW, sh),
			)
		}

		return result
	}

	private fun ByteArray.decodeXorCipher(): ByteArray {
		val k = "kotatsuanddokiarethebest"
			.toByteArray(Charsets.UTF_8)

		return this.mapIndexed { i, b ->
			(b.toInt() xor k[i % k.size].toInt()).toByte()
		}.toByteArray()
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val url = "https://$domain/$apiSuffix/genres"
		val response = webClient.httpGet(url).parseJsonArray()
		return response.mapJSONToSet { jo ->
			MangaTag(
				title = jo.getString("name").toTitleCase(sourceLocale),
				key = jo.getLong("id").toString(),
				source = source,
			)
		}
	}

	companion object {
		private const val GT = "gt="
	}
}
