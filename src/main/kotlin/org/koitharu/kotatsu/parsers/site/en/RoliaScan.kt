package org.koitharu.kotatsu.parsers.site.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("ROLIASCAN", "Rolia Scan", "en")
internal class RoliaScan(context: MangaLoaderContext) : LegacyPagedMangaParser(context, MangaParserSource.ROLIASCAN, 25) {

    override val configKeyDomain = ConfigKey.Domain("roliascan.com")

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.NEWEST,
        SortOrder.POPULARITY,
        SortOrder.ALPHABETICAL,
        SortOrder.ALPHABETICAL_DESC
    )

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
            isSearchWithFiltersSupported = true,
            isMultipleTagsSupported = true
        )

    override suspend fun getFilterOptions() = MangaListFilterOptions(
        availableTags = fetchTags(),
        availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
        availableContentTypes = EnumSet.of(
            ContentType.MANGA,
            ContentType.MANHWA,
            ContentType.MANHUA,
            ContentType.COMICS
        )
    )

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val baseUrl = "https://roliascan.com/manga"
        val url = buildString {
            append(baseUrl)
            append("?page=$page")
            if (!filter.query.isNullOrEmpty()) {
                append("&_post_type_search_box=${filter.query.urlEncoded()}")
            }
            append("&_sort_posts=")
            append(
                when (order) {
                    SortOrder.NEWEST -> "update_oldest"
                    SortOrder.POPULARITY -> ""
                    SortOrder.ALPHABETICAL -> "title_a_z"
                    SortOrder.ALPHABETICAL_DESC -> "title_z_a"
                    else -> ""
                }
            )
            if (filter.tags.isNotEmpty()) {
                append("&_genres=")
                append(filter.tags.joinToString(",") { it.key })
            }
            if (filter.states.isNotEmpty()) {
                append("&status=")
                append(
                    when (filter.states.oneOrThrowIfMany()) {
                        MangaState.ONGOING -> "publishing"
                        MangaState.FINISHED -> "completed"
                        else -> ""
                    }
                )
            }
            if (filter.types.isNotEmpty()) {
                append("&type=")
                append(
                    when (filter.types.oneOrThrowIfMany()) {
                        ContentType.MANGA -> "manga"
                        ContentType.MANHWA -> "manhwa"
                        ContentType.MANHUA -> "manhua"
                        ContentType.COMICS -> "comics"
                        else -> ""
                    }
                )
            }
        }

        val doc = webClient.httpGet(url).parseHtml()
        return doc.select("div.post").map { element ->
            val href = element.selectFirstOrThrow("h6 a").attrAsRelativeUrl("href")
            Manga(
                id = generateUid(href),
                title = element.selectFirst("h6 a")?.text().orEmpty(),
                altTitles = emptySet(),
                url = href,
                publicUrl = href.toAbsoluteUrl("roliascan.com"),
                rating = RATING_UNKNOWN,
                contentRating = null,
                coverUrl = element.selectFirst("img")?.attrAsAbsoluteUrlOrNull("src"),
                tags = emptySet(),
                state = null,
                authors = emptySet(),
                source = source
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl("roliascan.com")).parseHtml()
        val statusText = doc.selectFirst("tr:has(th:contains(Status)) > td")?.text().orEmpty()
        val chapterListUrl = manga.url.toAbsoluteUrl("roliascan.com").removeSuffix("/") + "/chapterlist/"
        val chapterDoc = webClient.httpGet(chapterListUrl).parseHtml()
        return manga.copy(
            tags = doc.select("a[href*=genres]").mapToSet {
                MangaTag(
                    key = it.attr("href").substringAfterLast("/"),
                    title = it.text(),
                    source = source
                )
            },
            description = doc.select("div.card-body:has(h5:contains(Synopsis)) p")
                .filter { p -> p.text().isNotBlank() }
                .joinToString("\n") { it.text() },
            state = when {
                statusText.contains("publishing", true) -> MangaState.ONGOING
                statusText.contains("completed", true) -> MangaState.FINISHED
                else -> null
            },
            chapters = chapterDoc.select("a.seenchapter").mapChapters(reversed = true) { i, el ->
                val href = el.attrAsRelativeUrl("href")
                MangaChapter(
                    id = generateUid(href),
                    title = el.text(),
                    number = i + 1f,
                    volume = 0,
                    url = href,
                    scanlator = null,
                    uploadDate = 0L,
                    branch = null,
                    source = source
                )
            }
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.url.toAbsoluteUrl("roliascan.com")).parseHtml()
        return doc.select(".manga-child-the-content img").map {
            val url = it.requireSrc()
            MangaPage(
                id = generateUid(url),
                url = url,
                preview = null,
                source = source
            )
        }
    }

    private suspend fun fetchTags(): Set<MangaTag> {
        return setOf(
            MangaTag(key = "action", title = "Action", source = source),
            MangaTag(key = "adventure", title = "Adventure", source = source),
            MangaTag(key = "comedy", title = "Comedy", source = source),
            MangaTag(key = "crime", title = "Crime", source = source),
            MangaTag(key = "drama", title = "Drama", source = source),
            MangaTag(key = "fantasy", title = "Fantasy", source = source),

            MangaTag(key = "high-school", title = "High School", source = source),
            MangaTag(key = "sports", title = "Sports", source = source),
            MangaTag(key = "shonen", title = "Shonen", source = source),
            MangaTag(key = "martial-arts", title = "Martial Arts", source = source),
            MangaTag(key = "romance", title = "Romance", source = source)
        )
    }
}
