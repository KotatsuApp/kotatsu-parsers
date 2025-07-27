package org.koitharu.kotatsu.parsers.site.en

import androidx.collection.arraySetOf
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("DEMONICSCANS", "DemonicScans", "en")
internal class DemonicScans(context: MangaLoaderContext) :
    PagedMangaParser(context, MangaParserSource.DEMONICSCANS, 25) {

    override val configKeyDomain = ConfigKey.Domain("demonicscans.org")

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.NEWEST,
        SortOrder.ALPHABETICAL,
        SortOrder.ALPHABETICAL_DESC
    )

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
            isSearchWithFiltersSupported = true,
            isMultipleTagsSupported = true,
        )

    override suspend fun getFilterOptions() = MangaListFilterOptions(
        availableTags = fetchTags(),
    )

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val hasQuery = !filter.query.isNullOrEmpty()
        val isNewest = order == SortOrder.NEWEST && !hasQuery && filter.tags.isEmpty()
        val isAlpha = order == SortOrder.ALPHABETICAL && !hasQuery && filter.tags.isEmpty()
        val isAlphaDesc = order == SortOrder.ALPHABETICAL_DESC && !hasQuery && filter.tags.isEmpty()
        val url = when {
            hasQuery -> "https://$domain/search.php?manga=" + (filter.query ?: "")
            isNewest -> "https://$domain/lastupdates.php?list=$page"
            isAlpha -> buildString {
                append("https://$domain/advanced.php")
                append("?list=$page")
                append("&status=all")
                append("&orderby=NAME ASC")
                if (filter.tags.isNotEmpty()) {
                    filter.tags.forEach { tag ->
                        append("&genre[]=")
                        append(tag.key)
                    }
                }
            }
            isAlphaDesc -> buildString {
                append("https://$domain/advanced.php")
                append("?list=$page")
                append("&status=all")
                append("&orderby=NAME DESC")
                if (filter.tags.isNotEmpty()) {
                    filter.tags.forEach { tag ->
                        append("&genre[]=")
                        append(tag.key)
                    }
                }
            }
            else -> buildString {
                append("https://$domain/advanced.php")
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
            when {
                isNewest -> parseNewestManga(element)
                else -> parseNormalManga(element, hasQuery)
            }
        }
    }

    private fun parseNewestManga(element: Element): Manga {
        val info = element.selectFirst("div.updates-element-info")!!
        val anchor = info.selectFirst("a")!!
        val href = anchor.attr("href").let {
            if (it.startsWith("http", ignoreCase = true)) it else "https://$domain${if (it.startsWith("/")) it else "/$it"}"
        }
        return Manga(
            id = generateUid(href),
            title = anchor.ownText(),
            altTitles = emptySet(),
            url = href,
            publicUrl = href,
            rating = RATING_UNKNOWN,
            contentRating = null,
            coverUrl = element.selectFirst("div.thumb img")?.attr("src")?.let { url ->
                if (url.startsWith("http", ignoreCase = true)) url else "https://$domain$url"
            },
            tags = emptySet(),
            state = null,
            authors = emptySet(),
            source = source
        )
    }

    private fun parseNormalManga(element: Element, hasQuery: Boolean): Manga {
        val anchor = if (hasQuery) element else element.selectFirst("a")!!
        val href = anchor.attr("href").let {
            if (it.startsWith("http", ignoreCase = true)) it else "https://$domain${if (it.startsWith("/")) it else "/$it"}"
        }
        return Manga(
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
            coverUrl = anchor.selectFirst("img")?.attr("src")?.let { url ->
                if (url.startsWith("http", ignoreCase = true)) url else "https://$domain$url"
            },
            tags = emptySet(),
            state = null,
            authors = emptySet(),
            source = source
        )
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url).parseHtml()
        val info = doc.selectFirst("div#manga-info-container")
        val title = info?.selectFirst("h1.big-fat-titles")?.ownText().orEmpty()
        val thumbnail = info?.selectFirst("div#manga-page img")?.attrAsAbsoluteUrlOrNull("src")
        val genre = info?.select("div.genres-list > li")?.joinToString { it.text() } ?: ""
        val description = info?.selectFirst("div#manga-info-rightColumn > div > div.white-font")?.textOrNull()
        val author = info?.select("div#manga-info-stats > div:has(> li:eq(0):contains(Author)) > li:eq(1)")?.textOrNull()
        val statusText = info?.select("div#manga-info-stats > div:has(> li:eq(0):contains(Status)) > li:eq(1)")?.text()
        val state = when (statusText) {
            "Ongoing" -> MangaState.ONGOING
            "Completed" -> MangaState.FINISHED
            else -> null
        }

        val chapters = doc.select("div#chapters-list a.chplinks").mapChapters(reversed = true) { i, el ->
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            val href = el.attr("href").let {
                if (it.startsWith("http")) it else "https://$domain${if (it.startsWith("/")) it else "/$it"}"
            }
            val date = el.selectFirst("span")?.text()
            MangaChapter(
                id = generateUid(href),
                title = el.ownText(),
                number = i + 1f,
                volume = 0,
                url = href,
                scanlator = null,
                uploadDate = dateFormat.parseSafe(date),
                branch = null,
                source = source
            )
        }

        return manga.copy(
            title = title,
            coverUrl = thumbnail,
            tags = genre.split(", ").filter { it.isNotBlank() }.mapToSet {
                MangaTag(title = it.lowercase().replace(" ", "-").toTitleCase(sourceLocale), key = it, source)
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

    private fun fetchTags() = arraySetOf(
		MangaTag("Action", "1", source),
    	MangaTag("Adventure", "2", source),
    	MangaTag("Comedy", "3", source),
    	MangaTag("Cooking", "34", source),
    	MangaTag("Doujinshi", "25", source),
    	MangaTag("Drama", "4", source),
    	MangaTag("Ecchi", "19", source),
    	MangaTag("Fantasy", "5", source),
    	MangaTag("Gender Bender", "30", source),
    	MangaTag("Harem", "10", source),
   		MangaTag("Historical", "28", source),
    	MangaTag("Horror", "8", source),
    	MangaTag("Isekai", "33", source),
    	MangaTag("Josei", "31", source),
    	MangaTag("Martial Arts", "6", source),
    	MangaTag("Mature", "22", source),
    	MangaTag("Mecha", "32", source),
    	MangaTag("Mystery", "15", source),
    	MangaTag("One Shot", "26", source),
    	MangaTag("Psychological", "11", source),
    	MangaTag("Romance", "12", source),
    	MangaTag("School Life", "13", source),
    	MangaTag("Sci-fi", "16", source),
    	MangaTag("Seinen", "17", source),
    	MangaTag("Shoujo", "14", source),
    	MangaTag("Shoujo Ai", "23", source),
    	MangaTag("Shounen", "7", source),
    	MangaTag("Shounen Ai", "29", source),
    	MangaTag("Slice of Life", "21", source),
    	MangaTag("Smut", "27", source),
    	MangaTag("Sports", "20", source),
    	MangaTag("Supernatural", "9", source),
    	MangaTag("Tragedy", "18", source),
    	MangaTag("Webtoons", "24", source),
    )
}
