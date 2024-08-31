package org.koitharu.kotatsu.parsers.site.vi

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.network.UserAgents
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.Inflater

@MangaSourceParser("CUUTRUYEN", "CuuTruyen", "vi")
internal class CuuTruyenParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.CUUTRUYEN, 20), Interceptor {

    override val configKeyDomain = ConfigKey.Domain("cuutruyen.net", "nettrom.com", "hetcuutruyen.net", "cuutruyent9sv7.xyz")

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.UPDATED,
        SortOrder.POPULARITY,
        SortOrder.NEWEST,
    )

    override fun getRequestHeaders(): Headers = Headers.Builder()
        .add("User-Agent", UserAgents.KOTATSU)
        .build()

    private val decryptionKey = "3141592653589793"
    private val itemsPerPage: Int = 20

    override suspend fun getAvailableTags(): Set<MangaTag> = emptySet()

    private suspend fun getListPage(
        offset: Int,
        query: String?,
        tags: Set<MangaTag>?,
        sortOrder: SortOrder,
    ): List<Manga> {
        val page = offset / itemsPerPage + 1
        val url = buildString {
            if (!query.isNullOrEmpty()) {
                append("$domain/api/v2/mangas/search")
                append("?q=")
                append(query.urlEncoded())
                append("&page=")
                append(page.toString())
                append("&per_page=")
                append(itemsPerPage.toString())
            } else {
                append("$domain/api/v2/mangas")
                when (sortOrder) {
                    SortOrder.UPDATED -> append("/recently_updated")
                    SortOrder.POPULARITY -> append("/top")
                    SortOrder.NEWEST -> append("/recently_updated")
                    else -> append("/recently_updated")
                }
                append("?page=")
                append(page.toString())
            }
        }

        val json = webClient.httpGet(url).parseJson()
        val data = json.optJSONObject("data") ?: throw ParseException("Invalid response", url)

        return data.getJSONArray("data").mapJSON { jo ->
            Manga(
                id = generateUid(jo.getLong("id")),
                url = "/api/v2/mangas/${jo.getLong("id")}",
                publicUrl = "$domain/manga/${jo.getLong("id")}",
                title = jo.getString("name"),
                altTitle = null,
                coverUrl = jo.getString("cover_url"),
                largeCoverUrl = jo.getString("cover_mobile_url"),
                author = jo.getStringOrNull("author_name"),
                tags = emptySet(),
                state = null,
                description = null,
                isNsfw = false,
                source = source,
                rating = RATING_UNKNOWN,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val url = domain + manga.url
        val json = webClient.httpGet(url).parseJson().getJSONObject("data")
            ?: throw ParseException("Invalid response", url)

        return manga.copy(
            description = json.getString("description"),
            chapters = json.getJSONArray("chapters").mapJSON { jo ->
                MangaChapter(
                    id = generateUid(jo.getLong("id")),
                    name = jo.getString("name"),
                    number = jo.getInt("number"),
                    url = "/api/v2/chapters/${jo.getLong("id")}",
                    scanlator = jo.optString("group_name"),
                    uploadDate = parseChapterDate(jo.getString("created_at")),
                    branch = null,
                    source = source,
                )
            }.reversed(),
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val url = "$domain${chapter.url}"
        val json = webClient.httpGet(url).parseJson().getJSONObject("data")
            ?: throw ParseException("Invalid response", url)

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

        if (!request.url.host.contains("cuutruyen.net")) {
            return response
        }

        val body = response.body ?: return response
        val contentType = body.contentType()
        val bytes = body.bytes()

        val decrypted = try {
            decrypt(bytes)
        } catch (e: Exception) {
            bytes
        }

        val decompressed = try {
            decompress(decrypted)
        } catch (e: Exception) {
            decrypted
        }

        val newBody = decompressed.toResponseBody(contentType)
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

    private fun parseChapterDate(dateString: String): Long {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(dateString)?.time ?: 0L
    }
}
