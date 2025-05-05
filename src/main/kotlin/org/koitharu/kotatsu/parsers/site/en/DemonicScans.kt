package org.koitharu.kotatsu.parsers.site.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("DEMONICSCANS", "DemonicScans", "en")
internal class DemonicScans(context: MangaLoaderContext) :
    LegacyPagedMangaParser(context, MangaParserSource.DEMONICSCANS, 25) {

    override val configKeyDomain = ConfigKey.Domain("demonicscans.org")

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
        val hasQuery = !filter.query.isNullOrEmpty()
        val isNewest = order == SortOrder.NEWEST && !hasQuery && filter.tags.isEmpty()
        val url = when {
            hasQuery -> "https://demonicscans.org/search.php?manga=" + (filter.query ?: "")
            isNewest -> "https://demonicscans.org/lastupdates.php?list=$page"
            else -> buildString {
                append("https://demonicscans.org/advanced.php")
                append("?list=$page")
                append("&status=all")
                append("&orderby=VIEWS DESC")
                if (filter.tags.isNotEmpty()) {
                    filter.tags.forEach { tag ->
                        append("&genre[]=")
                        append(tag.key)
                    }
                }
            }
        }

        val doc = webClient.httpGet(url).parseHtml()
        val selector = when {
            hasQuery -> "body > a[href]"
            isNewest -> "div#updates-container > div.updates-element"
            else -> "div#advanced-content > div.advanced-element"
        }

        return doc.select(selector).map { element ->
            if (isNewest) {
                val info = element.selectFirst("div.updates-element-info")
                val anchor = info?.selectFirst("a")
                val href = anchor?.attr("href")?.let {
                    if (it.startsWith("http")) it else "https://demonicscans.org${if (it.startsWith("/")) it else "/$it"}"
                } ?: ""
                Manga(
                    id = generateUid(href),
                    title = anchor?.ownText().orEmpty(),
                    altTitles = emptySet(),
                    url = href,
                    publicUrl = href,
                    rating = RATING_UNKNOWN,
                    contentRating = null,
                    coverUrl = element.selectFirst("div.thumb img")?.attrAsAbsoluteUrlOrNull("src"),
                    tags = emptySet(),
                    state = null,
                    authors = emptySet(),
                    source = source
                )
            } else {
                val anchor = if (hasQuery) element else element.selectFirstOrThrow("a")
                val href = anchor.attr("href").let {
                    if (it.startsWith("http")) it else "https://demonicscans.org${if (it.startsWith("/")) it else "/$it"}"
                }
                Manga(
                    id = generateUid(href),
                    title = if (hasQuery)
                        element.selectFirst("div.seach-right > div")?.text().orEmpty()
                    else
                        element.selectFirst("h1")?.ownText().orEmpty(),
                    altTitles = emptySet(),
                    url = href,
                    publicUrl = href,
                    rating = RATING_UNKNOWN,
                    contentRating = null,
                    coverUrl = anchor.selectFirst("img")?.attrAsAbsoluteUrlOrNull("src"),
                    tags = emptySet(),
                    state = null,
                    authors = emptySet(),
                    source = source
                )
            }
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url).parseHtml()
        val info = doc.selectFirst("div#manga-info-container")
        val title = info?.selectFirst("h1.big-fat-titles")?.ownText().orEmpty()
        val thumbnail = info?.selectFirst("div#manga-page img")?.attrAsAbsoluteUrlOrNull("src")
        val genre = info?.select("div.genres-list > li")?.joinToString { it.text() } ?: ""
        val description = info?.selectFirst("div#manga-info-rightColumn > div > div.white-font")?.text().orEmpty()
        val author = info?.select("div#manga-info-stats > div:has(> li:eq(0):contains(Author)) > li:eq(1)")?.text()
        val statusText = info?.select("div#manga-info-stats > div:has(> li:eq(0):contains(Status)) > li:eq(1)")?.text()
        val state = when {
            statusText?.contains("Ongoing", true) == true -> MangaState.ONGOING
            statusText?.contains("Completed", true) == true -> MangaState.FINISHED
            else -> null
        }

        val chapters = doc.select("div#chapters-list a.chplinks").mapChapters(reversed = true) { i, el ->
            val href = el.attr("href").let {
                if (it.startsWith("http")) it else "https://demonicscans.org${if (it.startsWith("/")) it else "/$it"}"
            }
            MangaChapter(
                id = generateUid(href),
                title = el.ownText(),
                number = i + 1f,
                volume = 0,
                url = href,
                scanlator = null,
                uploadDate = el.selectFirst("span")?.text()?.let { dateStr ->
                    try {
                        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(dateStr)?.time ?: 0L
                    } catch (_: Exception) {
                        0L
                    }
                } ?: 0L,
                branch = null,
                source = source
            )
        }

        return manga.copy(
            title = title,
            coverUrl = thumbnail,
            tags = genre.split(", ").filter { it.isNotBlank() }.mapToSet {
                MangaTag(it.lowercase().replace(" ", "-"), it, source)
            },
            description = description,
            state = state,
            authors = if (author.isNullOrBlank()) emptySet() else setOf(author),
            chapters = chapters
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.url).parseHtml()
        return doc.select("div > img.imgholder").map {
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
        val genres = listOf(
            "1" to "Action",
            "2" to "Adventure",
            "3" to "Comedy",
            "34" to "Cooking",
            "25" to "Doujinshi",
            "4" to "Drama",
            "19" to "Ecchi",
            "5" to "Fantasy",
            "30" to "Gender Bender",
            "10" to "Harem",
            "28" to "Historical",
            "8" to "Horror",
            "33" to "Isekai",
            "31" to "Josei",
            "6" to "Martial Arts",
            "22" to "Mature",
            "32" to "Mecha",
            "15" to "Mystery",
            "26" to "One Shot",
            "11" to "Psychological",
            "12" to "Romance",
            "13" to "School Life",
            "16" to "Sci-fi",
            "17" to "Seinen",
            "14" to "Shoujo",
            "23" to "Shoujo Ai",
            "7" to "Shounen",
            "29" to "Shounen Ai",
            "21" to "Slice of Life",
            "27" to "Smut",
            "20" to "Sports",
            "9" to "Supernatural",
            "18" to "Tragedy",
            "24" to "Webtoons"
        )
        return genres.mapTo(mutableSetOf()) { (id, name) ->
            MangaTag(
                key = id,
                title = name,
                source = source
            )
        }
    }
}
