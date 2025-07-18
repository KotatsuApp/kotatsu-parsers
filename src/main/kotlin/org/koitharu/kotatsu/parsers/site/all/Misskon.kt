package org.koitharu.kotatsu.parsers.site.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MISSKON", "MissKon", type = ContentType.OTHER)
internal class Misskon(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.MISSKON, 24) {

    override val configKeyDomain = ConfigKey.Domain("misskon.com")

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.UPDATED,
        SortOrder.POPULARITY
    )

    override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities( isSearchSupported = true )

    override suspend fun getFilterOptions() = MangaListFilterOptions()

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = buildString {
            append("https://")
            append(domain)
            when {
                !filter.query.isNullOrEmpty() -> {
                    append("/page/$page/")
                    append("?s=")
                    append(filter.query.urlEncoded())
                }
                order == SortOrder.POPULARITY -> {
                    append("/top3/")
                }
                else -> {
                    append("/page/$page")
                }
            }
        }

        val doc = webClient.httpGet(url).parseHtml()
        return doc.select("article.item-list").map { article ->
            val titleEl = article.selectFirst(".post-box-title")!!
            val href = titleEl.selectFirst("a")?.attrAsRelativeUrl("href")
                ?: article.parseFailed("Cannot find manga link")
            
            Manga(
                id = generateUid(href),
                title = titleEl.text(),
                altTitles = emptySet(),
                url = href,
                publicUrl = href.toAbsoluteUrl(domain),
                rating = RATING_UNKNOWN,
                contentRating = ContentRating.ADULT,
                coverUrl = article.selectFirst(".post-thumbnail img")?.absUrl("data-src").orEmpty(),
                tags = setOf(),
                state = null,
                authors = emptySet(),
                source = source,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
        val postInnerEl = doc.selectFirst("article > .post-inner")!!

        return manga.copy(
            tags = postInnerEl.select(".post-tag > a").mapToSet { a ->
                MangaTag(
                    key = a.text().lowercase(),
                    title = a.text(),
                    source = source
                )
            },
            chapters = listOf(
                MangaChapter(
                    id = manga.id,
                    title = "Oneshot", // 1 album, idk
                    number = 1f,
                    volume = 0,
                    url = manga.url,
                    scanlator = null,
                    uploadDate = 0L,
                    branch = null,
                    source = source
                )
            )
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
        val basePageUrl = doc.selectFirst("link[rel=canonical]")?.absUrl("href")
            ?: chapter.url.toAbsoluteUrl(domain)

        val pages = mutableListOf<MangaPage>()
        val pageLinks = doc.select("div.post-inner div.page-link:nth-child(1) .post-page-numbers")
        
        if (pageLinks.isEmpty()) {
            // Single page gallery
            return doc.select("div.post-inner > div.entry > p > img")
                .mapNotNull { img -> img.absUrl("data-src") }
                .mapIndexed { i, url ->
                    MangaPage(
                        id = generateUid(url),
                        url = url,
                        preview = null,
                        source = source
                    )
                }
        }

        // Multi-page gallery
        pageLinks.forEachIndexed { index, pageEl ->
            val pageDoc = when (index) {
                0 -> doc
                else -> {
                    val url = "$basePageUrl${pageEl.text()}/"
                    webClient.httpGet(url).parseHtml()
                }
            }
            
            pages.addAll(
                pageDoc.select("div.post-inner > div.entry > p > img")
                    .mapNotNull { img -> img.absUrl("data-src") }
                    .map { url ->
                        MangaPage(
                            id = generateUid(url),
                            url = url,
                            preview = null,
                            source = source
                        )
                    }
            )
        }
        return pages
    }
}
