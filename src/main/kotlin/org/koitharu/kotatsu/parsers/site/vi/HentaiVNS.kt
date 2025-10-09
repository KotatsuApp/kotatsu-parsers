package org.koitharu.kotatsu.parsers.site.vi

import androidx.collection.ArrayMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.AbstractMangaParser
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 24

@MangaSourceParser("HENTAIVNS", "HentaiVNS", "vi", type = ContentType.HENTAI)
internal class HentaiVNSParser(context: MangaLoaderContext) :
    PagedMangaParser(context, MangaParserSource.HENTAIVNS, PAGE_SIZE), MangaParserAuthProvider {

    override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("hentaivn.su")

    override val authUrl: String
        get() = "https://$domain"

    override suspend fun isAuthorized(): Boolean =
        context.cookieJar.getCookies(domain).any { it.name == "id" }

    override suspend fun getUsername(): String {
    val response = webClient.httpGet("/api/user/me".toAbsoluteUrl(domain))
    
    if (response.isSuccessful) {
        val userObject = response.parseJson()
        return userObject.optString("displayName", userObject.getString("username"))
     } else {
        // Nếu response không thành công (ví dụ: cookie hết hạn), ném exception mà framework yêu cầu
        throw AuthRequiredException(source, IllegalStateException("Failed to get user info: ${response.code}"))
     }
    }

    override suspend fun getFavicons(): Favicons = Favicons(
        listOf(Favicon("https://hentaivn.su/favicon.ico", 512, null)),
        domain
    )

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        super.onCreateConfig(keys)
    }

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.UPDATED,
        SortOrder.POPULARITY,
        SortOrder.RATING,
        SortOrder.NEWEST
    )

    override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
        isMultipleTagsSupported = true,
        isSearchSupported = true
    )

    override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions(
        availableTags = getOrCreateTagMap().values.toSet()
    )

    override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val page = (offset / PAGE_SIZE.toFloat()).toIntUp() + 1
        
        val apiUrl = urlBuilder().run {
            addPathSegments("api/library")
            when {
                !filter.query.isNullOrEmpty() -> {
                    addPathSegments("search")
                    addQueryParameter("q", filter.query)
                }
                filter.tags.isNotEmpty() -> {
                    addPathSegments("advanced-search")
                    val included = filter.tags.joinToString(",") { "(${it.key},1)" }
                    addQueryParameter("g", included)
                }
                else -> {
                    when (order) {
                        SortOrder.NEWEST -> addPathSegments("new")
                        SortOrder.POPULARITY, SortOrder.RATING -> addPathSegments("trending")
                        else -> addPathSegments("latest")
                    }
                }
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", PAGE_SIZE.toString())
            build()
        }

        val responseJson = webClient.httpGet(apiUrl).body!!.string()
        
        val mangaArray = if (responseJson.startsWith("[")) {
            JSONArray(responseJson)
        } else {
            JSONObject(responseJson).optJSONArray("data")
        } ?: JSONArray()

        return mangaArray.mapJSONNotNull { jo ->
            val id = jo.optLong("id", -1L).takeIf { it != -1L } ?: return@mapJSONNotNull null
            
            Manga(
                id = generateUid(id),
                title = jo.getString("title"),
                url = "/manga/$id",
                publicUrl = "/manga/$id".toAbsoluteUrl(domain),
                coverUrl = jo.getString("coverUrl").toAbsoluteUrl(domain),
                authors = jo.optString("authors", null)?.let { setOf(it) } ?: emptySet(),
                tags = jo.optJSONArray("genres")?.mapJSONToSet { genreJo ->
                    MangaTag(genreJo.getString("name"), genreJo.getString("id"), source)
                } ?: emptySet(),
                source = source,
                contentRating = sourceContentRating,
                altTitles = emptySet(),
                rating = RATING_UNKNOWN,
                state = null
            )
        }
    }
    
    override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
        val mangaId = manga.url.substringAfterLast('/')
        val detailsDeferred = async {
            webClient.httpGet("/api/manga/$mangaId".toAbsoluteUrl(domain)).parseJson()
        }
        val chaptersDeferred = async { fetchChaptersFromApi(mangaId) }

        val detailsJson = detailsDeferred.await()
        val chapters = chaptersDeferred.await()
        
        manga.copy(
            altTitles = detailsJson.optJSONArray("alternativeTitles")?.asTypedList<String>()?.toSet() ?: emptySet(),
            authors = detailsJson.optJSONArray("authors")?.mapJSONToSet { it.getString("name") } ?: emptySet(),
            description = detailsJson.optString("description", ""),
            tags = detailsJson.optJSONArray("genres")?.mapJSONToSet { genreJo ->
                MangaTag(genreJo.getString("name"), genreJo.getString("id"), source)
            } ?: emptySet(),
            chapters = chapters.map { it.copy(scanlator = detailsJson.optJSONObject("uploader")?.optString("name")) }
        )
    }
    
    private suspend fun fetchChaptersFromApi(mangaId: String): List<MangaChapter> {
        val apiUrl = "/api/manga/$mangaId/chapters".toAbsoluteUrl(domain)
        return try {
            webClient.httpGet(apiUrl).parseJsonArray().mapJSON { jo ->
                MangaChapter(
                    id = generateUid(jo.getLong("id")),
                    title = jo.getString("title"),
                    number = jo.getInt("readOrder").toFloat(),
                    url = "/chapter/${jo.getLong("id")}",
                    uploadDate = parseDate(jo.optString("createdAt", null)) ?: 0L,
                    source = source,
                    scanlator = null,
                    volume = 0,
                    branch = null
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val chapterId = chapter.url.substringAfterLast('/')
        val apiUrl = "/api/chapter/$chapterId".toAbsoluteUrl(domain)
        val chapterData = webClient.httpGet(apiUrl).parseJson()
        
        return chapterData.getJSONArray("pages").asTypedList<String>().map { imageUrl ->
            MangaPage(id = generateUid(imageUrl), url = imageUrl.toAbsoluteUrl(domain), source = source, preview = null)
        }
    }

    private suspend fun getOrCreateTagMap(): Map<String, MangaTag> {
        val apiUrl = "/api/tag/genre".toAbsoluteUrl(domain)
        val genres = webClient.httpGet(apiUrl).parseJsonArray()
        
        val tagMap = ArrayMap<String, MangaTag>()
        for (i in 0 until genres.length()) {
            val genre = genres.getJSONObject(i)
            val name = genre.getString("name")
            val id = genre.getString("id")
            tagMap[name] = MangaTag(title = name, key = id, source = source)
        }
        return tagMap
    }

    private fun parseDate(dateStr: String?): Long? {
        if (dateStr == null) return null
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        
        return try {
            sdf.parse(dateStr)?.time
        } catch (e: ParseException) {
            try {
                val simplerSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                simplerSdf.parse(dateStr)?.time
            } catch (e2: ParseException) { null }
        }
    }
}
