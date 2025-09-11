package org.koitharu.kotatsu.parsers.site.vi

import androidx.collection.arraySetOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.json.JSONObject
import java.util.*

@MangaSourceParser("GOCTRUYENTRANHVUI", "Góc Truyện Tranh Vui", "vi")
internal class GocTruyenTranhVui(context: MangaLoaderContext):
    PagedMangaParser(context, MangaParserSource.GOCTRUYENTRANHVUI, 50) {

    override val configKeyDomain = ConfigKey.Domain("goctruyentranhvui17.com")
    private val apiUrl by lazy { "https://$domain/api/v2" }

    private val requestMutex = Mutex()
    private var lastRequestTime = 0L

    private val apiHeaders by lazy {
        Headers.Builder()
            .add("Authorization", TOKEN_KEY)
            .add("Referer", "https://$domain/")
            .add("X-Requested-With", "XMLHttpRequest")
            .build()
    }

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.UPDATED,
        SortOrder.POPULARITY,
        SortOrder.NEWEST,
        SortOrder.RATING
    )

    override val filterCapabilities = MangaListFilterCapabilities(
        isSearchSupported = true,
        isMultipleTagsSupported = true,
    )

    override suspend fun getFilterOptions() = MangaListFilterOptions(
        availableTags = availableTags(),
        availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED)
    )

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        enforceRateLimit()
        val url = buildString {
            append(apiUrl)
            append("/search?p=${page - 1}")
            if (!filter.query.isNullOrBlank()) {
                append("&searchValue=${filter.query.urlEncoded()}")
            }

            val sortValue = when (order) {
                SortOrder.POPULARITY -> "viewCount"
                SortOrder.NEWEST -> "createdAt"
                SortOrder.RATING -> "evaluationScore"
                else -> "recentDate" // UPDATED
            }
            append("&orders%5B%5D=$sortValue")

            filter.tags.forEach { append("&categories%5B%5D=${it.key}") }

            filter.states.forEach {
                val statusKey = when (it) {
                    MangaState.ONGOING -> "PRG"
                    MangaState.FINISHED -> "END"
                    else -> null
                }
                if (statusKey != null) append("&status%5B%5D=$statusKey")
            }
        }

        val json = webClient.httpGet(url, extraHeaders = apiHeaders).parseJson()
        val result = json.optJSONObject("result") ?: return emptyList()
        val data = result.optJSONArray("data") ?: return emptyList()

        return List(data.length()) { i ->
            val item = data.getJSONObject(i)
            val comicId = item.getString("id")
            val slug = item.getString("nameEn")
            val mangaUrl = "/truyen/$slug"
            val tags = item.optJSONArray("category")?.let { arr ->
                (0 until arr.length()).mapNotNullTo(mutableSetOf()) { index ->
                    val tagName = arr.getString(index)
                    availableTags().find { it.title.equals(tagName, ignoreCase = true) }?.let { genrePair ->
                        MangaTag(key = genrePair.key, title = genrePair.title, source = source)
                    }
                }
            } ?: emptySet()

            Manga(
                id = generateUid(comicId),
                title = item.getString("name"),
                altTitles = item.optString("otherName", "").split(",").mapNotNull { it.trim().takeIf(String::isNotBlank) }.toSet(),
                url = "$comicId:$slug", // Store both id and slug, separated by ':'
                publicUrl = "https://$domain$mangaUrl",
                rating = item.optDouble("evaluationScore", 0.0).toFloat(),
                contentRating = null,
                coverUrl = "https://$domain${item.getString("photo")}",
                tags = tags,
                state = when (item.optString("statusCode")) {
                    "PRG" -> MangaState.ONGOING
                    "END" -> MangaState.FINISHED
                    else -> null
                },
                authors = setOf(item.optString("author", "Updating")),
                source = source
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val comicId = manga.url.substringBefore(':')
        val slug = manga.url.substringAfter(':')

        val chapters = try {
            enforceRateLimit()
            val chapterApiUrl = "https://$domain/api/comic/$comicId/chapter?limit=-1"
            val chapterJson = webClient.httpGet(chapterApiUrl, extraHeaders = apiHeaders).parseJson()
            val chaptersData = chapterJson.getJSONObject("result").getJSONArray("chapters")

            List(chaptersData.length()) { i ->
                val item = chaptersData.getJSONObject(i)
                val number = item.getString("numberChapter")
                val name = item.getString("name")
                val chapterUrl = "/truyen/$slug/chuong-$number"
                MangaChapter(
                    id = generateUid(chapterUrl),
                    title = if (name != "N/A" && name.isNotBlank()) name else "Chapter $number",
                    number = number.toFloatOrNull() ?: -1f,
                    volume = 0,
                    url = chapterUrl,
                    scanlator = null,
                    uploadDate = item.optLong("updateTime", 0L),
                    branch = null,
                    source = source
                )
            }
        } catch (_: Exception) {
            emptyList()
        }.reversed()

        enforceRateLimit()
        val doc = webClient.httpGet(manga.publicUrl).parseHtml()

        val detailTags = doc.select(".group-content > .v-chip-link").mapNotNullTo(mutableSetOf()) { el ->
            availableTags().find { it.title.equals(el.text(), ignoreCase = true) }?.let {
                MangaTag(key = it.key, title = it.title, source = source)
            }
        }

        return manga.copy(
            title = doc.selectFirst(".v-card-title")?.text().orEmpty(),
            tags = manga.tags + detailTags,
            coverUrl = doc.selectFirst("img.image")?.absUrl("src"),
            state = when (doc.selectFirst(".mb-1:contains(Trạng thái:) span")?.text()) {
                "Đang thực hiện" -> MangaState.ONGOING
                "Hoàn thành" -> MangaState.FINISHED
                else -> manga.state
            },
            authors = setOfNotNull(doc.selectFirst(".mb-1:contains(Tác giả:) span")?.text()),
            description = doc.selectFirst(".v-card-text")?.text(),
            chapters = chapters
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        enforceRateLimit()
        val responseBody = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).body.string()
        val chapterJsonRaw = responseBody.substringAfter("chapterJson: `", "").substringBefore("`", "")

        val imageUrls: List<String>
        if (chapterJsonRaw.isNotBlank()) {
            val json = JSONObject(chapterJsonRaw)
            val data = json.getJSONObject("body").getJSONObject("result").getJSONArray("data")
            imageUrls = List(data.length()) { i -> data.getString(i) }
        } else {
            // Fallback: Call the authenticated API
            val comicId = responseBody.substringAfter("comic = {id:\"", "").substringBefore("\"", "")
            val chapterNumber = chapter.url.substringAfterLast("chuong-")
            val nameEn = chapter.url.substringAfter("/truyen/").substringBefore("/chuong-")

            if (comicId.isBlank()) {
                throw Exception("Cannot find comicId in HTML for fallback image request")
            }

            val formBody = mapOf(
                "comicId" to comicId,
                "chapterNumber" to chapterNumber,
                "nameEn" to nameEn
            )
            val authApiUrl = "$apiUrl/chapter/auth".toHttpUrl()
            val authResponse = webClient.httpPost(url = authApiUrl, form = formBody, extraHeaders = apiHeaders).parseJson()
            val data = authResponse.getJSONObject("result").getJSONArray("data")
            imageUrls = List(data.length()) { i -> data.getString(i) }
        }

        return imageUrls.map { url ->
            val finalUrl = if (url.startsWith("/image/")) "https://$domain$url" else url
            MangaPage(id = generateUid(finalUrl), url = finalUrl, preview = null, source = source)
        }
    }

    private suspend fun enforceRateLimit() {
        requestMutex.withLock {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastRequest = currentTime - lastRequestTime
            if (timeSinceLastRequest < REQUEST_DELAY_MS) { // Vẫn truy cập được REQUEST_DELAY_MS
                delay(REQUEST_DELAY_MS - timeSinceLastRequest)
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }

    private fun availableTags() = arraySetOf(
        MangaTag("Anime", "ANI", source),
        MangaTag("Drama", "DRA", source),
        MangaTag("Josei", "JOS", source),
        MangaTag("Manhwa", "MAW", source),
        MangaTag("One Shot", "OSH", source),
        MangaTag("Shounen", "SHO", source),
        MangaTag("Webtoons", "WEB", source),
        MangaTag("Shoujo", "SHJ", source),
        MangaTag("Harem", "HAR", source),
        MangaTag("Ecchi", "ECC", source),
        MangaTag("Mature", "MAT", source),
        MangaTag("Slice of life", "SOL", source),
        MangaTag("Isekai", "ISE", source),
        MangaTag("Manga", "MAG", source),
        MangaTag("Manhua", "MAU", source),
        MangaTag("Hành Động", "ACT", source),
        MangaTag("Phiêu Lưu", "ADV", source),
        MangaTag("Hài Hước", "COM", source),
        MangaTag("Võ Thuật", "MAA", source),
        MangaTag("Huyền Bí", "MYS", source),
        MangaTag("Lãng Mạn", "ROM", source),
        MangaTag("Thể Thao", "SPO", source),
        MangaTag("Học Đường", "SCL", source),
        MangaTag("Lịch Sử", "HIS", source),
        MangaTag("Kinh Dị", "HOR", source),
        MangaTag("Siêu Nhiên", "SUN", source),
        MangaTag("Bi Kịch", "TRA", source),
        MangaTag("Trùng Sinh", "RED", source),
        MangaTag("Game", "GAM", source),
        MangaTag("Viễn Tưởng", "FTS", source),
        MangaTag("Khoa Học", "SCF", source),
        MangaTag("Truyện Màu", "COI", source),
        MangaTag("Người Lớn", "ADU", source),
        MangaTag("BoyLove", "BBL", source),
        MangaTag("Hầm Ngục", "DUN", source),
        MangaTag("Săn Bắn", "HUNT", source),
        MangaTag("Ngôn Từ Nhạy Cảm", "NTNC", source),
        MangaTag("Doujinshi", "DOU", source),
        MangaTag("Bạo Lực", "BLM", source),
        MangaTag("Ngôn Tình", "NTT", source),
        MangaTag("Nữ Cường", "NCT", source),
        MangaTag("Gender Bender", "GDB", source),
        MangaTag("Murim", "MRR", source),
        MangaTag("Leo Tháp", "LTT", source),
        MangaTag("Nấu Ăn", "COO", source)
    )

    companion object {
        private const val REQUEST_DELAY_MS = 350L
        private const val TOKEN_KEY = "Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJEcmFrZW4iLCJjb21pY0lkcyI6W10sInJvbGVJZCI6bnVsbCwiZ3JvdXBJZCI6bnVsbCwiYWRtaW4iOmZhbHNlLCJyYW5rIjowLCJwZXJtaXNzaW9uIjpbXSwiaWQiOiIwMDAxMTE1OTg2IiwidGVhbSI6ZmFsc2UsImlhdCI6MTc1NzU5NjQxMSwiZW1haWwiOiJudWxsIn0.VcGDaVQvyowtvja04CTUpfCP5XiC5qIdPmANZL0Gjz2kjz__PJ8LATQ9s44FpNohMpgLgPQO0TVs67D_YFlLNw"
    }
}
