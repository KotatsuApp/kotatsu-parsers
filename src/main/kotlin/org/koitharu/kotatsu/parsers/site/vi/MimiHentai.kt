package org.koitharu.kotatsu.parsers.site.vi

import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MIMIHENTAI", "MimiHentai", "vi", type = ContentType.HENTAI)
internal class MimiHentai(context: MangaLoaderContext) :
    LegacyPagedMangaParser(context, MangaParserSource.MIMIHENTAI, 18) {

    private val apiSuffix = "api/v1/manga"
    override val configKeyDomain = ConfigKey.Domain("mimihentai.com")

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

    override suspend fun getFilterOptions() = MangaListFilterOptions(availableTags = fetchTags())
    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
            isSearchWithFiltersSupported = true,
            isMultipleTagsSupported = true,
            isAuthorSearchSupported = true
        )

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = buildString {
            append("https://")
            append(domain)
            append("/$apiSuffix/advance-search?page=")
            append(page - 1) // first page is 0, not 1
            append("&max=18") // page size, avoid rate limit
            when {
                !filter.query.isNullOrEmpty() -> {
                    append("&name=")
                    append(filter.query.urlEncoded())
                }

                !filter.author.isNullOrEmpty() -> {
                    append("&author=")
                    append(filter.author.urlEncoded())
                }
                
                filter.tags.isNotEmpty() -> {
                    append("&genre=")
                    append(filter.tags.joinToString(",") { it.key })
                }
            }
        }

        val json = webClient.httpGet(url).parseJson()
        val data = json.getJSONArray("data")
        return parseMangaList(data)
    }

    private suspend fun parseMangaList(data: JSONArray): List<Manga> {
        return data.mapJSON { jo ->
            val id = jo.getLong("id")
            val title = jo.getString("title")
            val description = jo.getString("description")
            val authors = jo.getJSONArray("authors").asTypedList<String>().mapToSet { it }
            val differentNames = jo.getJSONArray("differentNames").asTypedList<String>().mapToSet { it }
            val state = when(description) {
                "Đang Tiến Hành" -> MangaState.ONGOING
                "Hoàn Thành" -> MangaState.FINISHED
                else -> null
            }
            
            Manga(
                id = generateUid(id),
                title = title,
                altTitles = differentNames,
                url = "/$apiSuffix/info/$id",
                publicUrl = "https://$domain/g/$id",
                rating = RATING_UNKNOWN,
                contentRating = ContentRating.ADULT,
                coverUrl = jo.getString("coverUrl"),
                tags = emptySet(),
                state = state,
                authors = authors,
                source = source,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
        val url = "https://" + domain + manga.url
        val json = webClient.httpGet(url).parseJson()
        
        val relationInfo = json.getJSONObject("relationInfo")
        val tags = relationInfo.getJSONArray("genres").mapJSON { jo ->
            MangaTag(
                title = jo.getString("name"),
                key = jo.getLong("id").toString(),
                source = source,
            )
        }.toSet()

        val basicInfo = json.getJSONObject("basicInfo")
        val id = basicInfo.getLong("id")
        val description = basicInfo.optString("fdescription").takeUnless { it.isNullOrEmpty() }
        val uploaderName = json.getString("uploaderName")
        val urlChaps = "https://$domain/$apiSuffix/gallery/$id"
        val parseUrlChaps = async { JSONArray(webClient.httpGet(urlChaps).parseHtml().text()) }
        val chapters = parseUrlChaps.await().mapJSON { jo ->
            MangaChapter(
                id = generateUid(jo.getLong("id")),
                title = jo.getString("title"),
                number = jo.getInt("number").toFloat(),
                url = "/$apiSuffix/chapter?id=${jo.getLong("id")}",
                uploadDate = 0L,
                source = source,
                scanlator = uploaderName,
                branch = null,
                volume = 0
            )
        }

        manga.copy(
            tags = tags,
            description = description,
            chapters = chapters
        )
    }

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val json = webClient.httpGet("https://$domain${chapter.url}").parseJson()
        val imageUrls = json.getJSONArray("pages").asTypedList<String>()
        return imageUrls.map { url ->
            MangaPage(
                id = generateUid(url),
                url = url,
                preview = null,
                source = source,
            )
        }
    }

    private suspend fun fetchTags(): Set<MangaTag> {
        val url = "https://$domain/$apiSuffix/genres"
        val response = JSONArray(webClient.httpGet(url).parseHtml().text())
        return response.mapJSON { jo ->
            MangaTag(
                title = jo.getString("name"),
                key = jo.getLong("id").toString(),
                source = source,
            )
        }.toSet()
    }
}