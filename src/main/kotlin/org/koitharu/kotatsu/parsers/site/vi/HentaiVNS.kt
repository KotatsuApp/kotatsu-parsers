package org.koitharu.kotatsu.parsers.site.vi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.collection.ArrayMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider // <-- IMPORT MỚI
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.AbstractMangaParser
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException // <-- IMPORT MỚI
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

// --- DATA CLASSES ---
@Serializable data class User(val id: Int, val username: String, val displayName: String? = null)
@Serializable data class ApiResponse<T>(val data: List<T>, val page: Int? = null, val total: Int? = null)
@Serializable data class MangaListItem(val id: Int, val title: String, val coverUrl: String, val authors: String? = null, val genres: List<GenreItem> = emptyList(), val blocked: Boolean = false)
@Serializable data class GenreItem(val id: Int, val name: String)
@Serializable data class AuthorItem(val id: Int, val name: String)
@Serializable data class Uploader(val id: Int, val name: String)
@Serializable data class MangaDetails(val id: Int, val title: String, val alternativeTitles: List<String> = emptyList(), val coverUrl: String, val description: String?, val authors: List<AuthorItem> = emptyList(), val genres: List<GenreItem> = emptyList(), val uploader: Uploader? = null)
@Serializable data class ChapterItem(val id: Int, val title: String, val readOrder: Int, @SerialName("createdAt") val createdAt: String)
@Serializable data class ChapterDetails(@SerialName("pages") val imageUrls: List<String>)


@MangaSourceParser("HENTAIVNS", "HentaiVNS", "vi", type = ContentType.HENTAI)
internal class HentaiVNSParser(context: MangaLoaderContext) :
    AbstractMangaParser(context, MangaParserSource.HENTAIVNS), MangaParserAuthProvider {

    override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("hentaivn.su")
    private val json = Json { ignoreUnknownKeys = true }

    override val authUrl: String
        get() = domain

    override suspend fun isAuthorized(): Boolean =
        context.cookieJar.getCookies(domain).any { it.name == "id" }

    override suspend fun getUsername(): String {
        try {
            val response = webClient.httpGet("/api/user/me".toAbsoluteUrl(domain))
            if (response.isSuccessful) {
                val userJson = response.body!!.string()
                val user = json.decodeFromString<User>(userJson)
                return user.displayName ?: user.username
            } else {
                throw IllegalStateException("Failed to get user info: ${response.code}")
            }
        } catch (e: Exception) {
            throw AuthRequiredException(source, e)
        }
    }



    override suspend fun getFavicons(): Favicons = Favicons(
        listOf(Favicon("https://favicone.com/hentaivn.su?s=256", 512, null)),
        domain
    )

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        super.onCreateConfig(keys)
        keys.add(userAgentKey)
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
        val page = (offset / 24f).toIntUp() + 1
        val apiUrl = buildString {
            append("/api/library/")
            when {
                !filter.query.isNullOrEmpty() -> append("search?q=${filter.query.urlEncoded()}&page=$page")
                filter.tags.isNotEmpty() -> { 
                    val included = filter.tags.joinToString(",") { "(${it.key},1)" }
                    append("advanced-search?g=${included.urlEncoded()}&page=$page")
                }
                else -> {
                    when (order) {
                        SortOrder.NEWEST -> append("new?page=$page")
                        SortOrder.POPULARITY, SortOrder.RATING -> append("trending?page=$page")
                        else -> append("latest?page=$page")
                    }
                }
            }
        }.toAbsoluteUrl(domain)
        val responseJson = webClient.httpGet(apiUrl).body!!.string()
        val mangaList: List<MangaListItem> = try {
            json.decodeFromString<ApiResponse<MangaListItem>>(responseJson).data
        } catch (e: Exception) {
            json.decodeFromString<List<MangaListItem>>(responseJson)
        }
        return mangaList.filterNot { it.blocked }.map { item ->
            Manga(
                id = generateUid(item.id.toString()),
                title = item.title,
                url = "/manga/${item.id}",
                publicUrl = "/manga/${item.id}".toAbsoluteUrl(domain),
                coverUrl = item.coverUrl.toAbsoluteUrl(domain),
                authors = setOfNotNull(item.authors),
                tags = item.genres.mapToSet { genre -> MangaTag(genre.name, genre.id.toString(), source) },
                source = source,
                contentRating = ContentRating.ADULT,
                altTitles = emptySet(),
                rating = RATING_UNKNOWN,
                state = null
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
        val mangaId = manga.url.substringAfterLast('/')
        val detailsDeferred = async {
            val apiUrl = "/api/manga/$mangaId".toAbsoluteUrl(domain)
            val responseJson = webClient.httpGet(apiUrl).body!!.string()
            json.decodeFromString<MangaDetails>(responseJson)
        }
        val chaptersDeferred = async { fetchChaptersFromApi(mangaId) }
        val details = detailsDeferred.await()
        val chapters = chaptersDeferred.await()
        manga.copy(
            altTitles = details.alternativeTitles.toSet(),
            authors = details.authors.mapToSet { it.name },
            description = details.description ?: "",
            tags = details.genres.mapToSet { genre -> MangaTag(genre.name, genre.id.toString(), source) },
            chapters = chapters.map { it.copy(scanlator = details.uploader?.name) }
        )
    }

    private suspend fun fetchChaptersFromApi(mangaId: String): List<MangaChapter> {
        val apiUrl = "/api/manga/$mangaId/chapters".toAbsoluteUrl(domain)
        return try {
            val responseJson = webClient.httpGet(apiUrl).body!!.string()
            val chapterItems = json.decodeFromString<List<ChapterItem>>(responseJson)
            chapterItems.map { chapterItem ->
                MangaChapter(
                    id = generateUid(chapterItem.id.toString()),
                    title = chapterItem.title,
                    number = chapterItem.readOrder.toFloat(),
                    url = "/chapter/${chapterItem.id}",
                    uploadDate = parseDate(chapterItem.createdAt) ?: 0L,
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
        val responseJson = webClient.httpGet(apiUrl).body!!.string()
        val chapterData = json.decodeFromString<ChapterDetails>(responseJson)
        return chapterData.imageUrls.map { imageUrl ->
            MangaPage(id = generateUid(imageUrl), url = imageUrl.toAbsoluteUrl(domain), source = source, preview = null)
        }
    }

    private var tagCache: ArrayMap<String, MangaTag>? = null
    private val mutex = Mutex()

    private suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
        tagCache?.let { return@withLock it }
        val apiUrl = "/api/tag/genre".toAbsoluteUrl(domain)
        val responseJson = webClient.httpGet(apiUrl).body!!.string()
        val genres = json.decodeFromString<List<GenreItem>>(responseJson)
        val tagMap = ArrayMap<String, MangaTag>()
        for (genre in genres) {
            tagMap[genre.name] = MangaTag(title = genre.name, key = genre.id.toString(), source = source)
        }
        tagCache = tagMap
        return@withLock tagMap
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
