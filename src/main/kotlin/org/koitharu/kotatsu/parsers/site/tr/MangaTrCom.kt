package org.koitharu.kotatsu.parsers.site.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANGATR", "Manga-TR", "tr")
internal class MangaTR(context: MangaLoaderContext) : LegacyPagedMangaParser(context, MangaParserSource.MANGATR, 2) {
    override val configKeyDomain = ConfigKey.Domain("manga-tr.com")

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.NEWEST,
        SortOrder.UPDATED,
        SortOrder.POPULARITY,
    )

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isMultipleTagsSupported = false,
            isTagsExclusionSupported = false,
            isSearchSupported = true,
        )

    override suspend fun getFilterOptions() = MangaListFilterOptions(
        availableTags = emptySet(),
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
            append("https://manga-tr.com/manga-list-sayfala.html?page=$page")
            if (!filter.query.isNullOrEmpty()) {
                append("&icerik=")
                append(filter.query.urlEncoded())
            }
        }
        val doc = webClient.httpGet(url).parseHtml()
        return doc.select("div.row a[data-toggle]")
            .filterNot { it.siblingElements().text().contains("Novel") }
            .map { element ->
                val href = element.attrAsRelativeUrl("href")
                Manga(
                    id = generateUid(href),
                    title = element.text().orEmpty(),
                    altTitles = emptySet(),
                    url = href,
                    publicUrl = href.toAbsoluteUrl("manga-tr.com"),
                    rating = RATING_UNKNOWN,
                    contentRating = null,
                    coverUrl = null,
                    tags = emptySet(),
                    state = null,
                    authors = emptySet(),
                    source = source,
                )
            }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl("manga-tr.com")).parseHtml()
        val infoElement = doc.selectFirst("div#tab1")
        val author = infoElement?.selectFirst("table + table tr + tr td:nth-child(1) a")?.text()
        val artist = infoElement?.selectFirst("table + table tr + tr td:nth-child(2) a")?.text()
        val genre = infoElement?.selectFirst("table + table tr + tr td:nth-child(3)")?.text()
        val description = infoElement?.selectFirst("div.well")?.ownText()?.trim()
        val thumbnail = doc.selectFirst("img.thumbnail")?.attrAsAbsoluteUrlOrNull("src")
        val statusText = infoElement?.selectFirst("tr:contains(Çeviri Durumu) + tr > td:nth-child(2)")?.text().orEmpty()
        return manga.copy(
            altTitles = emptySet(),
            authors = setOfNotNull(author, artist),
            tags = if (genre != null) setOf(MangaTag(key = genre.lowercase(Locale.ROOT), title = genre, source = source)) else emptySet(),
            description = description,
            coverUrl = thumbnail,
            state = when {
                statusText.contains("Devam", ignoreCase = true) -> MangaState.ONGOING
                statusText.contains("Tamam", ignoreCase = true) -> MangaState.FINISHED
                else -> null
            },
            chapters = getChapters(manga, doc),
        )
    }

    private fun getChapters(manga: Manga, doc: org.jsoup.nodes.Document): List<MangaChapter> {
        val id = manga.url.substringAfter("manga-").substringBefore(".")
        val requestUrl = "https://manga-tr.com/cek/fetch_pages_manga.php?manga_cek=$id"
        val firstDoc = doc
        val chapters = mutableListOf<MangaChapter>()
        var nextPage = 2
        var currentDoc = firstDoc
        do {
            chapters.addAll(currentDoc.select("tr.table-bordered").mapIndexed { i, el ->
                val a = el.selectFirst("td[align=left] > a")
                val href = a?.attr("href") ?: ""
                MangaChapter(
                    id = generateUid(href + i),
                    title = a?.text().orEmpty(),
                    number = (i + 1).toFloat(),
                    volume = 0,
                    url = href,
                    scanlator = null,
                    uploadDate = el.selectFirst("td[align=right]")?.text()?.let { parseDate(it) } ?: 0L,
                    branch = null,
                    source = source,
                )
            })
            val nextPageLink = currentDoc.selectFirst("a[data-page=$nextPage]")
            if (nextPageLink != null) {
                val body = mapOf("page" to nextPage.toString())
                val resp = webClient.httpPost(requestUrl, body = body).parseHtml()
                currentDoc = resp
                nextPage++
            } else {
                break
            }
        } while (true)
        return chapters
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val url = "https://manga-tr.com/${chapter.url.substringAfter("cek/")}"
        val doc = webClient.httpGet(url).parseHtml()
        return doc.select("img.img-responsive").mapIndexed { idx, el ->
            val imgUrl = el.attrAsAbsoluteUrlOrNull("src") ?: return@mapIndexed null
            MangaPage(
                id = generateUid(imgUrl + idx),
                url = imgUrl,
                preview = null,
                source = source,
            )
        }.filterNotNull()
    }

    private fun parseDate(date: String): Long? {
        return try {
            DATE_FORMAT.parse(date)?.time
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("dd.MM.yyyy", Locale("tr"))
    }
}
