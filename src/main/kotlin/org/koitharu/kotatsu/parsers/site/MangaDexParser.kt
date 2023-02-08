package org.koitharu.kotatsu.parsers.site

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 20
private const val CHAPTERS_FIRST_PAGE_SIZE = 120
private const val CHAPTERS_MAX_PAGE_SIZE = 500
private const val CHAPTERS_PARALLELISM = 3
private const val CHAPTERS_MAX_COUNT = 10_000 // strange api behavior, looks like a bug
private const val CONTENT_RATING =
    "contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica&contentRating[]=pornographic"
private const val LOCALE_FALLBACK = "en"

@MangaSourceParser("MANGADEX", "MangaDex")
internal class MangaDexParser(context: MangaLoaderContext) : MangaParser(context, MangaSource.MANGADEX) {

    override val configKeyDomain = ConfigKey.Domain("mangadex.org", null)

    override val sortOrders: EnumSet<SortOrder> = EnumSet.of(
        SortOrder.UPDATED,
        SortOrder.ALPHABETICAL,
        SortOrder.NEWEST,
        SortOrder.POPULARITY,
    )

    override suspend fun getList(
        offset: Int,
        query: String?,
        tags: Set<MangaTag>?,
        sortOrder: SortOrder,
    ): List<Manga> {
        val domain = domain
        val url = buildString {
            append("https://api.")
            append(domain)
            append("/manga?limit=")
            append(PAGE_SIZE)
            append("&offset=")
            append(offset)
            append("&includes[]=cover_art&includes[]=author&includes[]=artist&")
            tags?.forEach { tag ->
                append("includedTags[]=")
                append(tag.key)
                append('&')
            }
            if (!query.isNullOrEmpty()) {
                append("title=")
                append(query.urlEncoded())
                append('&')
            }
            append(CONTENT_RATING)
            append("&order")
            append(
                when (sortOrder) {
                    SortOrder.UPDATED,
                    -> "[latestUploadedChapter]=desc"

                    SortOrder.ALPHABETICAL -> "[title]=asc"
                    SortOrder.NEWEST -> "[createdAt]=desc"
                    SortOrder.POPULARITY -> "[followedCount]=desc"
                    else -> "[followedCount]=desc"
                },
            )
        }
        val json = webClient.httpGet(url).parseJson().getJSONArray("data")
        return json.mapJSON { jo ->
            val id = jo.getString("id")
            val attrs = jo.getJSONObject("attributes")
            val relations = jo.getJSONArray("relationships").associateByKey("type")
            val cover = relations["cover_art"]
                ?.getJSONObject("attributes")
                ?.getString("fileName")
                ?.let {
                    "https://uploads.$domain/covers/$id/$it"
                }
            Manga(
                id = generateUid(id),
                title = requireNotNull(attrs.getJSONObject("title").selectByLocale()) {
                    "Title should not be null"
                },
                altTitle = attrs.optJSONObject("altTitles")?.selectByLocale(),
                url = id,
                publicUrl = "https://$domain/title/$id",
                rating = RATING_UNKNOWN,
                isNsfw = attrs.getStringOrNull("contentRating") == "erotica",
                coverUrl = cover?.plus(".256.jpg").orEmpty(),
                largeCoverUrl = cover,
                description = attrs.optJSONObject("description")?.selectByLocale(),
                tags = attrs.getJSONArray("tags").mapJSONToSet { tag ->
                    MangaTag(
                        title = tag.getJSONObject("attributes")
                            .getJSONObject("name")
                            .firstStringValue()
                            .toTitleCase(),
                        key = tag.getString("id"),
                        source = source,
                    )
                },
                state = when (jo.getStringOrNull("status")) {
                    "ongoing" -> MangaState.ONGOING
                    "completed" -> MangaState.FINISHED
                    else -> null
                },
                author = (relations["author"] ?: relations["artist"])
                    ?.getJSONObject("attributes")
                    ?.getStringOrNull("name"),
                source = source,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
        val domain = domain
        val mangaId = manga.url.removePrefix("/")
        val attrsDeferred = async {
            webClient.httpGet(
                "https://api.$domain/manga/${mangaId}?includes[]=artist&includes[]=author&includes[]=cover_art",
            ).parseJson().getJSONObject("data").getJSONObject("attributes")
        }
        val feedDeferred = async { loadChapters(mangaId) }
        val mangaAttrs = attrsDeferred.await()
        val feed = feedDeferred.await()
        // 2022-01-02T00:27:11+00:00
        val dateFormat = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'+00:00'",
            Locale.ROOT,
        )
        manga.copy(
            description = mangaAttrs.optJSONObject("description")?.selectByLocale()
                ?: manga.description,
            chapters = feed.mapChapters { _, jo ->
                val id = jo.getString("id")
                val attrs = jo.getJSONObject("attributes")
                if (!attrs.isNull("externalUrl")) {
                    return@mapChapters null
                }
                val locale = attrs.getStringOrNull("translatedLanguage")?.let { Locale.forLanguageTag(it) }
                val relations = jo.getJSONArray("relationships").associateByKey("type")
                val number = attrs.getIntOrDefault("chapter", 0)
                MangaChapter(
                    id = generateUid(id),
                    name = attrs.getStringOrNull("title")?.takeUnless(String::isEmpty)
                        ?: "Chapter #$number",
                    number = number,
                    url = id,
                    scanlator = relations["scanlation_group"]?.getStringOrNull("name"),
                    uploadDate = dateFormat.tryParse(attrs.getString("publishAt")),
                    branch = locale?.getDisplayName(locale)?.toTitleCase(locale),
                    source = source,
                )
            },
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val domain = domain
        val chapterJson = webClient.httpGet("https://api.$domain/at-home/server/${chapter.url}?forcePort443=false")
            .parseJson()
            .getJSONObject("chapter")
        val pages = chapterJson.getJSONArray("data")
        val prefix = "https://uploads.$domain/data/${chapterJson.getString("hash")}/"
        val referer = "https://$domain/"
        return List(pages.length()) { i ->
            val url = prefix + pages.getString(i)
            MangaPage(
                id = generateUid(url),
                url = url,
                referer = referer,
                preview = null, // TODO prefix + dataSaver.getString(i),
                source = source,
            )
        }
    }

    override suspend fun getTags(): Set<MangaTag> {
        val tags = webClient.httpGet("https://api.${domain}/manga/tag").parseJson()
            .getJSONArray("data")
        return tags.mapJSONToSet { jo ->
            MangaTag(
                title = jo.getJSONObject("attributes").getJSONObject("name").firstStringValue().toTitleCase(),
                key = jo.getString("id"),
                source = source,
            )
        }
    }

    private fun JSONObject.firstStringValue() = values().next() as String

    private fun JSONObject.selectByLocale(): String? {
        val preferredLocales = context.getPreferredLocales()
        for (locale in preferredLocales) {
            getStringOrNull(locale.language)?.let { return it }
            getStringOrNull(locale.toLanguageTag())?.let { return it }
        }
        return getStringOrNull(LOCALE_FALLBACK) ?: values().nextOrNull() as? String
    }

    private suspend fun loadChapters(mangaId: String): List<JSONObject> {
        val firstPage = loadChapters(mangaId, offset = 0, limit = CHAPTERS_FIRST_PAGE_SIZE)
        if (firstPage.size >= firstPage.total) {
            return firstPage.data
        }
        val tail = coroutineScope {
            val leftCount = firstPage.total.coerceAtMost(CHAPTERS_MAX_COUNT) - firstPage.size
            val pages = (leftCount / CHAPTERS_MAX_PAGE_SIZE.toFloat()).toIntUp()
            val dispatcher = Dispatchers.Default.limitedParallelism(CHAPTERS_PARALLELISM)
            List(pages) { page ->
                val offset = page * CHAPTERS_MAX_PAGE_SIZE + firstPage.size
                async(dispatcher) {
                    loadChapters(mangaId, offset, CHAPTERS_MAX_PAGE_SIZE)
                }
            }.awaitAll()
        }
        val result = ArrayList<JSONObject>(firstPage.total)
        result += firstPage.data
        tail.flatMapTo(result) { it.data }
        return result
    }

    private suspend fun loadChapters(mangaId: String, offset: Int, limit: Int): Chapters {
        val limitedLimit = when {
            offset >= CHAPTERS_MAX_COUNT -> return Chapters(emptyList(), CHAPTERS_MAX_COUNT)
            offset + limit > CHAPTERS_MAX_COUNT -> CHAPTERS_MAX_COUNT - offset
            else -> limit
        }
        val url = buildString {
            append("https://api.")
            append(domain)
            append("/manga/")
            append(mangaId)
            append("/feed")
            append("?limit=")
            append(limitedLimit)
            append("&includes[]=scanlation_group&order[volume]=asc&order[chapter]=asc&offset=")
            append(offset)
            append('&')
            append(CONTENT_RATING)
        }
        val json = webClient.httpGet(url).parseJson()
        if (json.getString("result") == "ok") {
            return Chapters(
                data = json.optJSONArray("data")?.toJSONList().orEmpty(),
                total = json.getInt("total"),
            )
        } else {
            val error = json.optJSONArray("errors").mapJSON { jo ->
                jo.getString("detail")
            }.joinToString("\n")
            throw ParseException(error, url)
        }
    }

    private class Chapters(
        val data: List<JSONObject>,
        val total: Int,
    ) {

        val size: Int
            get() = data.size
    }
}
