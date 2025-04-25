package org.koitharu.kotatsu.parsers.site.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacySinglePageMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("RAGNARSCANS", "RagnarScans", "tr")
internal class RagnarScans(context: MangaLoaderContext) :
    LegacySinglePageMangaParser(context, MangaParserSource.RAGNARSCANS) {

    override val configKeyDomain = ConfigKey.Domain("ragnarscans.com")

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        super.onCreateConfig(keys)
        keys.add(userAgentKey)
    }

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.UPDATED,
        SortOrder.POPULARITY,
        SortOrder.ALPHABETICAL
    )

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
            isSearchWithFiltersSupported = true,
        )

    override suspend fun getFilterOptions() = MangaListFilterOptions()

    override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
        val results = mutableListOf<Manga>()
        var page = 1
        var emptyPageCount = 0
        
        while (true) {
            val mangas = getListPage(page, order, filter)
            
            if (mangas.isEmpty()) {
                emptyPageCount++
                if (emptyPageCount >= 2) break
            } else {
                emptyPageCount = 0
                results.addAll(mangas)
                
                if (!filter.query.isNullOrBlank() && mangas.size < 10) break
            }
            
            if (filter.query.isNullOrBlank() && page >= 10) break
            page++
        }
        return results
    }

    private suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = buildString {
            append("https://")
            append(domain)
            if (filter.query.isNullOrBlank()) {
                append("/manga/")
                if (page > 1) append("page/$page/")
                when (order) {
                    SortOrder.POPULARITY -> append("?m_orderby=popular")
                    SortOrder.ALPHABETICAL -> append("?m_orderby=title")
                    else -> append("?m_orderby=latest")
                }
            } else {
                append("/page/$page/?s=")
                append(filter.query.urlEncoded())
                append("&post_type=wp-manga")
            }
        }

        val doc = try {
            webClient.httpGet(url).parseHtml()
        } catch (e: Exception) {
            return emptyList()
        }

        if (page > 1) {
            val activePage = doc.selectFirst(".page-numbers.current")?.text()?.toIntOrNull()
            if (activePage != page) return emptyList()
        }

        val mangaDivs = doc.select(".page-item-detail.manga, .row.c-tabs-item__content")
        if (mangaDivs.isEmpty()) return emptyList()

        return mangaDivs.mapNotNull { div ->
            try {
                val a = div.selectFirstOrThrow("a")
                val href = a.attrAsRelativeUrl("href")
                val titleElement = div.selectFirstOrThrow(".post-title, .post-title.font-title")
                val title = titleElement.text()
                
                val img = div.selectFirst("img")?.let { 
                    it.attr("data-src").ifBlank { it.attr("src") } 
                }?.toAbsoluteUrl(domain) ?: ""
                
                Manga(
                    id = generateUid(href),
                    title = title,
                    altTitles = emptySet(),
                    url = href,
                    publicUrl = href.toAbsoluteUrl(domain),
                    rating = RATING_UNKNOWN,
                    contentRating = null,
                    coverUrl = img,
                    tags = emptySet(),
                    state = null,
                    authors = emptySet(),
                    source = source,
                )
            } catch (e: Exception) {
                null
            }
        }.take(10)
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
        
        val author = doc.select(".author-content a, .manga-detail .author a").firstOrNull()?.textOrNull()
        val genres = doc.select(".genres-content a, .manga-detail .genres a").mapNotNull { it.textOrNull() }.toSet()
        val genreTags = genres.map { MangaTag(it, "genre", source) }.toSet()
        val statusText = doc.select(".post-status .summary-content, .manga-detail .status").firstOrNull()?.textOrNull()?.trim()
        val description = doc.selectFirstOrThrow(".summary__content.show-more, .description-summary").html()
        
        val state = when (statusText?.lowercase()) {
            "devam ediyor", "ongoing" -> MangaState.ONGOING
            "tamamlandı", "completed" -> MangaState.FINISHED
            else -> null
        }

        val chapters = mutableListOf<MangaChapter>()
        var currentPage = 1
        val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr"))
        
        while (true) {
            val chapterListUrl = if (currentPage == 1) {
                manga.url.toAbsoluteUrl(domain) + "ajax/chapters/"
            } else {
                manga.url.toAbsoluteUrl(domain) + "ajax/chapters/?page=$currentPage"
            }
            
            val chapterDoc = try {
                webClient.httpPost(
                    url = chapterListUrl,
                    form = mapOf("action" to "manga_get_chapters")
                ).parseHtml()
            } catch (e: Exception) {
                break
            }
            
            val chapterElements = chapterDoc.select("li.wp-manga-chapter, li.chapter-li")
            if (chapterElements.isEmpty()) break
            
            chapterElements.map { li ->
                val a = li.selectFirstOrThrow("a")
                val url = a.attrAsRelativeUrl("href")
                val title = a.text()
                val dateStr = li.select(".chapter-release-date i, .chapter-release-date").firstOrNull()?.textOrNull()
                
                MangaChapter(
                    id = generateUid(url),
                    title = title,
                    number = -(chapters.size + 1).toFloat(), 
                    volume = 0,
                    url = url,
                    scanlator = null,
                    uploadDate = dateFormat.tryParse(dateStr),
                    branch = null,
                    source = source,
                )
            }.reversed().forEach { chapters.add(it) }
            
            currentPage++
        }

        return manga.copy(
            state = state,
            authors = setOfNotNull(author),
            description = description,
            chapters = chapters,
            tags = genreTags,
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val fullUrl = chapter.url.toAbsoluteUrl(domain)
        val doc = webClient.httpGet(fullUrl).parseHtml()
        
        return doc.select(".wp-manga-chapter-img, .page-break img").mapNotNull { img ->
            val rawUrl = img.attr("src") ?: return@mapNotNull null
            
            val fixedUrl = when {
                rawUrl.contains("https://$domain/https:/") -> rawUrl.replace(
                    "https://$domain/https:/", 
                    "https:/"
                )
                rawUrl.contains("https://$domain/http:/") -> rawUrl.replace(
                    "https://$domain/http:/", 
                    "http:/"
                )
                rawUrl.startsWith("/") -> rawUrl.toAbsoluteUrl(domain)
                else -> rawUrl
            }.removeSuffix("/") 
            
            MangaPage(
                id = generateUid(fixedUrl),
                url = fixedUrl,
                preview = null,
                source = source,
            )
        }.sortedBy { it.url } 
    }
}
