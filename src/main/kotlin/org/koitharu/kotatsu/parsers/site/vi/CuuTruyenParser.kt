package org.koitharu.kotatsu.parsers.site.vi

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.Inflater

@MangaSourceParser("CUUTRUYEN", "CuuTruyen", "vi")
internal class CuuTruyenParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.CUUTRUYEN, 20), Interceptor {

	override val configKeyDomain =
		ConfigKey.Domain("cuutruyen.net", "nettrom.com", "hetcuutruyen.net", "cuutruyent9sv7.xyz")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
	)

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("User-Agent", UserAgents.KOTATSU)
		.build()

	private val decryptionKey = "3141592653589793"

	override suspend fun getAvailableTags(): Set<MangaTag> = emptySet()

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {
				is MangaListFilter.Search -> {
					append("/api/v2/mangas/search?q=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				else -> {
					val tag = (filter as? MangaListFilter.Advanced)?.tags?.oneOrThrowIfMany()
					if (tag != null) {
						append("/api/v2/tags/")
						append(tag.key)
					} else {
						append("/api/v2/mangas")
						when (filter?.sortOrder) {
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

		val json = webClient.httpGet(url).parseJson()
		val data = json.getJSONArray("data")

		return data.mapJSON { jo ->
			Manga(
				id = generateUid(jo.getLong("id")),
				url = "/api/v2/mangas/${jo.getLong("id")}",
				publicUrl = "https://$domain/manga/${jo.getLong("id")}",
				title = jo.getString("name"),
				altTitle = null,
				coverUrl = jo.getString("cover_url"),
				largeCoverUrl = jo.getString("cover_mobile_url"),
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
			val imageUrl = jo.getString("image_url")
			MangaPage(
				id = generateUid(jo.getLong("id")),
				url = imageUrl,
				preview = null,
				source = source,
			)
		}
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val response = chain.proceed(request)

		if (!request.url.host.contains(domain, ignoreCase = true)) {
			return response
		}

		val body = response.body ?: return response
		val contentType = body.contentType()
		val bytes = body.bytes()

		val decrypted = try {
			decompress(decrypt(bytes))
		} catch (e: Exception) {
			bytes
		}
		val newBody = decrypted.toResponseBody(contentType)
		return response.newBuilder().body(newBody).build()
	}

	private fun decrypt(input: ByteArray): ByteArray {
		val key = decryptionKey.toByteArray()
		return input.mapIndexed { index, byte ->
			(byte.toInt() xor key[index % key.size].toInt()).toByte()
		}.toByteArray()
	}

	private fun decompress(input: ByteArray): ByteArray {
		val inflater = Inflater()
		inflater.setInput(input, 0, input.size)
		val outputStream = ByteArrayOutputStream(input.size)
		val buffer = ByteArray(1024)
		while (!inflater.finished()) {
			val count = inflater.inflate(buffer)
			outputStream.write(buffer, 0, count)
		}
		return outputStream.toByteArray()
	}
}
