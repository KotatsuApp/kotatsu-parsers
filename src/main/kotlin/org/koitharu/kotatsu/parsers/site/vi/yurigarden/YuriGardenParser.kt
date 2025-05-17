package org.koitharu.kotatsu.parsers.site.vi.yurigarden

import androidx.collection.arraySetOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.util.*

internal abstract class YuriGardenParser(
    context: MangaLoaderContext,
    source: MangaParserSource,
    domain: String,
    protected val isR18Enable: Boolean = false
) : LegacyPagedMangaParser(context, source, 18) {

    private val availableTags = suspendLazy(initializer = ::fetchTags)

    override val configKeyDomain = ConfigKey.Domain(domain)
    protected val apiSuffix = "api.$domain"

    override val userAgentKey = ConfigKey.UserAgent(UserAgents.KOTATSU)
    
    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        super.onCreateConfig(keys)
        keys.add(userAgentKey)
    }

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.RELEVANCE,
        SortOrder.NEWEST,
        SortOrder.NEWEST_ASC,
        SortOrder.POPULARITY,
        SortOrder.RATING,
    )

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
            isMultipleTagsSupported = true,
            isSearchWithFiltersSupported = true,
        )

    override suspend fun getFilterOptions(): MangaListFilterOptions {
        return MangaListFilterOptions(
            availableTags = availableTags.get(),
            availableStates = EnumSet.of(
                MangaState.ONGOING,
                MangaState.FINISHED,
                MangaState.ABANDONED,
                MangaState.PAUSED,
            ),
        )
    }

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = buildString {
            append("https://")
            append(apiSuffix)
            append("/comics")
            append("?page=")
            append(page)
            append("&limit=")
            append(pageSize)
            append("&r18=")
            append(isR18Enable)

            append("&sort=")
            append(when (order) {
                SortOrder.RELEVANCE -> "relevance"
                SortOrder.NEWEST -> "newest"
                SortOrder.NEWEST_ASC -> "oldest"
                SortOrder.RATING -> "rating"
                SortOrder.POPULARITY -> "popularity"
                else -> "relevance"
            })

            if (!filter.query.isNullOrEmpty()) {
                append("&search=")
                append(filter.query.urlEncoded())
            }

            if (filter.states.isNotEmpty()) {
                filter.states.oneOrThrowIfMany()?.let {
                    append("&status=")
                    append(when (it) {
                        MangaState.ONGOING -> "ongoing"
                        MangaState.FINISHED -> "completed"
                        MangaState.PAUSED -> "hiatus"
                        MangaState.ABANDONED -> "cancelled"
                        else -> ""
                    })
                }
            }

            if (filter.tags.isNotEmpty()) {
				append("&genre=")
				append(filter.tags.joinToString(separator = ",") { it.key })
			}
        }

        val json = webClient.httpGet(url).parseJson()
        val data = json.getJSONArray("comics")

        return data.mapJSON { jo ->
            val id = jo.getLong("id")
            Manga(
                id = generateUid(id),
                url = "/comics/$id",
                publicUrl = "https://$domain/comic/$id",
                title = jo.getString("title"),
                altTitles = emptySet(),
                coverUrl = jo.getString("thumbnail"),
                largeCoverUrl = jo.getString("thumbnail"),
                authors = emptySet(),
                tags = emptySet(),
                state = when(jo.getString("status")) {
                    "ongoing" -> MangaState.ONGOING
                    "completed" -> MangaState.FINISHED
                    "hiatus" -> MangaState.PAUSED
                    "cancelled" -> MangaState.ABANDONED
                    else -> null
                },
                description = jo.getString("description"),
                contentRating = if (jo.getBooleanOrDefault("r18", false)) ContentRating.ADULT else ContentRating.SUGGESTIVE,
                source = source,
                rating = jo.getFloatOrDefault("rating", -5f) / 5f,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
        val url = "https://" + apiSuffix + manga.url
        val chapters = async {
            webClient.httpGet("$url/chapters").parseJsonArray()
        }
        val json = webClient.httpGet(url).parseJson()

        val authors = json.optJSONArray("authors")?.mapJSONToSet { jo ->
            jo.getString("name")
        }.orEmpty()

        val team = json.optJSONArray("teams")?.let { teamsArray ->
            teamsArray.getJSONObject(0).getString("name")
        }

        val allTags = fetchTags().orEmpty()
        val tags = allTags.let { allTags ->
            json.optJSONArray("genres")?.asTypedList<String>()?.mapNotNullToSet { g ->
                allTags.find { x -> x.key == g }
            }
        }.orEmpty()

        manga.copy(
            title = json.getString("title"),
            altTitles = setOf(json.getString("anotherName")),
            contentRating = if (json.getBooleanOrDefault("r18", false)) {
                ContentRating.ADULT
            } else {
                ContentRating.SUGGESTIVE
            },
            authors = authors,
            tags = tags,
            description = json.getString("description"),
            state = when(json.getString("status")) {
                "ongoing" -> MangaState.ONGOING
                "completed" -> MangaState.FINISHED
                "hiatus" -> MangaState.PAUSED
                "cancelled" -> MangaState.ABANDONED
                else -> null
            },
            rating = json.getFloatOrDefault("rating", -5f) / 5f,
            chapters = chapters.await().mapChapters() { _, jo ->
                val chapterId = jo.getLong("id")
                val pageUrls = jo.getJSONArray("pages").mapJSON { page ->
                    page.getString("url")
                }
                MangaChapter(
                    id = generateUid(chapterId),
                    title = jo.getString("name"),
                    number = jo.getString("order").toFloat(),
                    volume = 0,
                    url = pageUrls.joinToString("\n"),
                    scanlator = team,
                    uploadDate = jo.getLong("lastUpdated"),
                    branch = null,
                    source = source,
                )
            },
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        return chapter.url.split("\n").mapIndexed { index, url ->
            MangaPage(
                id = generateUid(index.toLong()),
                url = url,
                preview = null,
                source = source,
            )
        }
    }

    private suspend fun fetchTags(): Set<MangaTag> {
        val json = webClient.httpGet("https://$apiSuffix/resources/systems_vi.json").parseJson()
        val genres = json.getJSONObject("genres")
        return genres.keys().asSequence().mapTo(arraySetOf()) { key ->
            val genre = genres.getJSONObject(key)
            MangaTag(
                title = genre.getString("name").toTitleCase(sourceLocale),
                key = genre.getString("slug"),
                source = source,
            )
        }
    }
}
