package org.koitharu.kotatsu.parsers.site.vi

import org.json.JSONArray
import org.json.JSONObject
import androidx.collection.ArrayMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import org.koitharu.kotatsu.parsers.util.suspendlazy.getOrNull
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*
import org.koitharu.kotatsu.parsers.Broken

@Broken
@MangaSourceParser("MIMIHENTAI", "MimiHentai", "vi", type = ContentType.HENTAI)
internal class MimiHentai(context: MangaLoaderContext) :
    LegacyPagedMangaParser(context, MangaParserSource.MIMIHENTAI, 20) {

    private val apiSuffix = "api/v1/manga"
    private val availableTags = suspendLazy(initializer = ::fetchTags)

    override val configKeyDomain = ConfigKey.Domain("mimihentai.com")
    override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

    override suspend fun getFilterOptions() = MangaListFilterOptions(availableTags = availableTags.get().values.toSet())
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
            append("&max=20") // page size
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
            
            val allTags = availableTags.getOrNull()?.values?.toSet().orEmpty()
            val genresArray = jo.getJSONArray("genres")
            val genres = (0 until genresArray.length()).mapNotNull { i ->
                val genreName = genresArray.getString(i)
                allTags.firstOrNull { it.title.equals(genreName, ignoreCase = true) }
            }.toSet()
            
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
                tags = genres,
                state = state,
                authors = authors,
                source = source,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
        val url = "https://" + domain + manga.url
        val json = webClient.httpGet(url).parseJson()
        
        val basicInfo = json.getJSONObject("basicInfo")
        val id = basicInfo.getLong("id")
        val state = when (basicInfo.getString("description")) {
            "Đang Tiến Hành" -> MangaState.ONGOING
            "Hoàn Thành" -> MangaState.FINISHED
            else -> null
        }
        val description = basicInfo.getString("fdescription")
        val uploaderName = json.getString("uploaderName")

        val relationInfo = json.getJSONObject("relationInfo")
        val authors = relationInfo.getJSONArray("authors").asTypedList<String>().mapToSet { it }
        val differentNames = relationInfo.getJSONArray("differentNames").asTypedList<String>().mapToSet { it }
        val tags = relationInfo.getJSONArray("genres").asTypedList<JSONObject>().mapToSet { jo ->
            MangaTag(
                title = jo.getString("name").toTitleCase(),
                key = jo.getLong("id").toString(),
                source = source,
            )
        }

        val chapters = async {
            webClient.httpGet("https://$domain/api/v1/manga/gallery/$id").parseJson().getJSONArray("data")
        }

        manga.copy(
            description = description,
            tags = tags,
            state = state,
            authors = authors,
            altTitles = differentNames,
            chapters = chapters.await().mapChapters(reversed = false) { i, jo ->
                val chapterId = jo.getLong("id")
                val title = jo.getString("title")
                MangaChapter(
                    id = generateUid(chapterId),
                    title = title,
                    number = i + 1f,
                    volume = 0,
                    url = "/$apiSuffix/chapter?id=$chapterId",
                    scanlator = uploaderName,
                    uploadDate = 0,
                    branch = null,
                    source = source,
                )
            },
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

    private suspend fun fetchTags(): Map<String, MangaTag> { // need fix
		val tagList = webClient.httpGet("$apiSuffix/genres".toAbsoluteUrl(domain)).parseJson()
		val tags = ArrayMap<String, MangaTag>()
		for (key in tagList.keys()) {
			val jo = tagList.getJSONObject(key)
			val name = jo.getString("name")
            val key = jo.getLong("id").toString()
			tags[name.lowercase()] = MangaTag(
				title = name.toTitleCase(),
				key = key,
				source = source,
			)
		}
		return tags
	}

    private fun JSONObject.parseJson(key: String): JSONObject {
		return JSONObject(getString(key))
	}
}
