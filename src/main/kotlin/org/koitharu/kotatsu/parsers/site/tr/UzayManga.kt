package org.koitharu.kotatsu.parsers.site.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("UZAYMANGA", "Uzay Manga", "tr")
internal class UzayManga(context: MangaLoaderContext) : LegacyPagedMangaParser(context, MangaParserSource.UZAYMANGA, 2) {

    override val configKeyDomain = ConfigKey.Domain("uzaymanga.com")

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.NEWEST,
        SortOrder.UPDATED,
        SortOrder.POPULARITY,
    )

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isMultipleTagsSupported = true,
            isTagsExclusionSupported = false,
            isSearchSupported = true,
        )

    override suspend fun getFilterOptions() = MangaListFilterOptions(
        availableTags = fetchAvailableTags(),
        availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
        availableContentTypes = EnumSet.of(
            ContentType.MANGA,
            ContentType.MANHWA,
            ContentType.MANHUA,
            ContentType.COMICS,
            ContentType.OTHER,
        ),
    )

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = buildString {
            append("https://uzaymanga.com/search?page=$page&search=")
            if (!filter.query.isNullOrEmpty()) {
                append(filter.query.urlEncoded())
            }
            append("&order=")
            append(
                when (order) {
                    SortOrder.NEWEST -> "3"
                    SortOrder.UPDATED -> "4"
                    SortOrder.POPULARITY -> "2"
                    else -> "3"
                }
            )
        }
        val doc = webClient.httpGet(url).parseHtml()
        return doc.select("section[aria-label='series area'] .card").map { card ->
            val href = card.selectFirstOrThrow("a").attrAsRelativeUrl("href")
            Manga(
                id = generateUid(href),
                title = card.selectFirst("h2")?.text().orEmpty(),
                altTitles = emptySet(),
                url = href,
                publicUrl = href.toAbsoluteUrl("uzaymanga.com"),
                rating = RATING_UNKNOWN,
                contentRating = null,
                coverUrl = card.selectFirst("img")?.attrAsAbsoluteUrlOrNull("src"),
                tags = emptySet(),
                state = null,
                authors = emptySet(),
                source = source,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl("uzaymanga.com")).parseHtml()
        val statusText = doc.selectFirst("span:contains(Durum) + span")?.text().orEmpty()
        return manga.copy(
            altTitles = emptySet(),
            authors = emptySet(),
            tags = doc.select("a[href^='search?categories']").mapToSet {
                MangaTag(
                    key = it.text().lowercase(Locale.ROOT),
                    title = it.text(),
                    source = source,
                )
            },
            description = doc.selectFirst("div.grid h2 + p")?.text(),
            state = when {
                statusText.contains("Devam Ediyor", ignoreCase = true) || statusText.contains("Birakildi", ignoreCase = true) -> MangaState.ONGOING
                statusText.contains("Tamamlandi", ignoreCase = true) -> MangaState.FINISHED
                else -> null
            },
            chapters = doc.select("div.list-episode a").mapChapters(reversed = true) { i, el ->
                val href = el.attrAsRelativeUrl("href")
                MangaChapter(
                    id = generateUid(href),
                    title = el.selectFirstOrThrow("h3").text(),
                    number = (i + 1).toFloat(),
                    volume = 0,
                    url = href,
                    scanlator = null,
                    uploadDate = el.selectFirst("span")?.text()?.let { parseDate(it) } ?: 0L,
                    branch = null,
                    source = source,
                )
            },
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.url.toAbsoluteUrl("uzaymanga.com")).parseHtml()
        val script = doc.select("script").map { it.html() }.firstOrNull { PAGE_REGEX.find(it) != null } ?: return emptyList()
        return PAGE_REGEX.findAll(script).mapIndexed { idx, result ->
            val url = result.groups[1]?.value ?: return@mapIndexed null
            MangaPage(
                id = generateUid(url),
                url = "https://cdn1.uzaymanga.com/upload/series/$url",
                preview = null,
                source = source,
            )
        }.filterNotNull().toList()
    }

    private suspend fun fetchAvailableTags(): Set<MangaTag> {
        return emptySet()
    }

    private fun parseDate(date: String): Long? {
        return try {
            DATE_FORMAT.parse(date)?.time
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("MMM d ,yyyy", Locale("tr"))
        private val PAGE_REGEX = Regex("\\\\\"path\\\\\":\\\\\"([^\"]+)\\\\\"")
    }
}
