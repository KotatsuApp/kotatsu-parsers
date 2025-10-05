package org.koitharu.kotatsu.parsers.site.fr

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MANGAMOINS", "MangaMoins", "mangamoins.shaeishu.co", type = ContentType.MANGA)
class MangaMoinsParser(context: MangaLoaderContext) :
    PagedMangaParser(context, MangaSource.MANGAMOINS, 20) {

    override val configKeyDomain = ConfigKey.Domain("mangamoins.shaeishu.co")

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

    override val filterCapabilities = MangaListFilterCapabilities(
        isSearchSupported = false,
        isTagsExclusionSupported = false,
        isMultipleTagsSupported = false,
        isSearchWithFiltersSupported = false,
        isYearRangeSupported = false,
        isAuthorSearchSupported = false,
    )

    override suspend fun getFilterOptions() = MangaListFilterOptions()

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val doc = webClient.httpGet("https://mangamoins.shaeishu.co/?p=$page").parseHtml()
        val mangas = mutableListOf<Manga>()

        for (el in doc.select("div.sortie")) {
            val link = el.selectFirst("a")!!
            val href = link.attr("href")
            val scanId = href.substringAfter("scan=").substringBefore("&").substringBefore("#")
            val titleEl = el.selectFirst("figcaption p")!!
            val title = titleEl.ownText().substringBefore("\n").trim()
            val author = titleEl.selectFirst("span")?.text()?.trim() ?: ""
            val coverUrl = el.selectFirst("img")!!.src().toAbsoluteUrl(domain)

            mangas.add(
                Manga(
                    id = generateUid(scanId),
                    title = "$title #$scanId",
                    url = href,
                    publicUrl = "https://mangamoins.shaeishu.co$href",
                    rating = RATING_UNKNOWN,
                    isNsfw = false,
                    coverUrl = coverUrl,
                    tags = emptySet(),
                    state = MangaState.ONGOING,
                    author = author,
                    source = source,
                )
            )
        }

        return mangas
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.publicUrl).parseHtml()
        val fullTitle = doc.selectFirst("title")?.text()?.removePrefix("MangaMoins | ") ?: manga.title
        val cleanTitle = fullTitle.substringBefore("#").trim()

        val chapter = MangaChapter(
            id = manga.id,
            name = manga.title,
            url = manga.url,
            publicUrl = manga.publicUrl,
            uploadDate = 0L,
            source = source,
            scanlator = "MangaMoins",
        )

        return manga.copy(
            title = cleanTitle,
            chapters = listOf(chapter),
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.publicUrl).parseHtml()
        return doc.select("link[rel=preload][as=image]").mapIndexed { index, el ->
            MangaPage(
                id = index.toLong(),
                url = el.attr("href").toAbsoluteUrl(domain),
                preview = null,
                source = source,
            )
        }
    }

    override fun getRequestHeaders() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .add("Referer", "https://mangamoins.shaeishu.co/")
        .build()
}
