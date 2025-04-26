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
            isSearchSupported = true,
            isMultipleTagsSupported = true,
        )

    override suspend fun getFilterOptions() = MangaListFilterOptions(
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
            append("https://")
			append(domain)
            append("/search")
            append("&page=")
			append(page.toString())

            if (!filter.query.isNullOrEmpty()) {
                append("&search")
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
            tags = doc.select("a[href^='search?categories']").mapToSet {
                MangaTag(
                    key = it.text().lowercase(Locale.ROOT),
                    title = it.text(),
                    source = source,
                )
            },
            description = doc.selectFirst("div.grid h2 + p")?.text(),
            state = when (statusText) {
                "Devam Ediyor" -> MangaState.ONGOING
                "Birakildi" -> MangaState.ONGOING
                "Tamamlandi" -> MangaState.FINISHED
                else -> null
            },
            chapters = doc.select("div.list-episode a").mapChapters(reversed = true) { i, el ->
                val href = el.attrAsRelativeUrl("href")
                val dateFormat = SimpleDateFormat("MMM d ,yyyy", Locale("tr"))
                MangaChapter(
                    id = generateUid(href),
                    title = el.selectFirstOrThrow("h3").text(),
                    number = (i + 1).toFloat(), 
                    volume = 0,
                    url = href,
                    scanlator = null,
                    uploadDate = el.selectFirst("span")?.text()?.let { dateFormat.tryParse(it) } ?: 0L,
                    branch = null,
                    source = source,
                )
            },
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.url.toAbsoluteUrl("uzaymanga.com")).parseHtml()
        val pageRegex = Regex("\\\\\"path\\\\\":\\\\\"([^\"]+)\\\\\"")
        val script = doc.select("script").find { it.html().contains(pageRegex) }?.html() ?: return emptyList()
        return pageRegex.findAll(script).mapNotNull { result ->
            result.groups[1]?.value?.let { url ->
                MangaPage(
                    id = generateUid(url),
                    url = "https://cdn1.uzaymanga.com/upload/series/$url",
                    preview = null, 
                    source = source,
                )
            }
        }.toList()
    }
}
