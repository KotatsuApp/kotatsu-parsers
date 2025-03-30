package org.koitharu.kotatsu.parsers.site.zh

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("HAPPYMH", "HappyMH", "zh")
internal class HappyMH(context: MangaLoaderContext) :
    LegacyPagedMangaParser(context, MangaParserSource.HAPPYMH, 20) {

    override val configKeyDomain = ConfigKey.Domain("m.happymh.com")

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        super.onCreateConfig(keys)
        keys.add(userAgentKey)
    }

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.UPDATED,
        SortOrder.POPULARITY,
        SortOrder.RATING,
        SortOrder.NEWEST
    )

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
            isMultipleTagsSupported = true,
            isSearchWithFiltersSupported = true
        )

    override suspend fun getFilterOptions() = MangaListFilterOptions(
        availableTags = fetchAvailableTags(),
        availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED)
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = buildString {
            append("https://")
            append(domain)
            
            if (!filter.query.isNullOrEmpty()) {
                append("/search?word=")
                append(filter.query.urlEncoded())
                append("&page=")
                append(page)
            } else {
                append("/latest?page=")
                append(page)
                
                when (order) {
                    SortOrder.UPDATED -> append("&sort=1")
                    SortOrder.POPULARITY -> append("&sort=2")
                    SortOrder.RATING -> append("&sort=3")
                    SortOrder.NEWEST -> append("&sort=4")
                    else -> append("&sort=1")
                }
                
                if (filter.tags.isNotEmpty()) {
                    filter.tags.forEach { tag ->
                        append("&tags=")
                        append(tag.key)
                    }
                }
                
                filter.state?.let {
                    append("&status=")
                    append(when (it) {
                        MangaState.ONGOING -> "1"
                        MangaState.FINISHED -> "2"
                        else -> "0"
                    })
                }
            }
        }
        
        val doc = webClient.httpGet(url).parseHtml()
        return doc.select("div.comics-item").map { item ->
            val href = item.selectFirst("a")?.attrAsRelativeUrl("href") ?: item.parseFailed("Manga link not found")
            val title = item.selectFirst("div.comics-title")?.text() ?: item.parseFailed("Manga title not found")
            val cover = item.selectFirst("img")?.attr("src")?.toAbsoluteUrl(domain) ?: ""
            
            Manga(
                id = generateUid(href),
                url = href,
                publicUrl = href.toAbsoluteUrl(domain),
                title = title,
                coverUrl = cover,
                altTitle = null,
                rating = RATING_UNKNOWN,
                tags = emptySet(),
                state = null,
                author = null,
                source = source
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
        val fullUrl = manga.url.toAbsoluteUrl(domain)
        val doc = webClient.httpGet(fullUrl).parseHtml()
        
        val chaptersDeferred = async { getChapters(doc) }
        
        val title = doc.selectFirst("h1.comics-title")?.text() ?: manga.title
        val altTitle = doc.selectFirst("div.comics-alt-title")?.text()
        val cover = doc.selectFirst("div.comics-cover img")?.attr("src")?.toAbsoluteUrl(domain) ?: manga.coverUrl
        
        val tags = doc.select("div.comics-tags a").mapToSet { element ->
            val tagName = element.text().trim()
            val tagHref = element.attrAsRelativeUrl("href")
            val tagKey = tagHref.substringAfterLast("=")
            MangaTag(title = tagName, key = tagKey, source = source)
        }
        
        // Adjust for Chinese status text
        val statusText = doc.selectFirst("div.comics-status")?.text()?.lowercase() ?: ""
        val state = when {
            statusText.contains("连载中") -> MangaState.ONGOING
            statusText.contains("已完结") -> MangaState.FINISHED
            else -> null
        }
        
        val author = doc.selectFirst("div.comics-author")?.text()
        val description = doc.selectFirst("div.comics-description")?.html()
        
        val rating = doc.selectFirst("div.comics-rating")?.text()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN
        
        manga.copy(
            title = title,
            altTitle = altTitle,
            coverUrl = cover,
            tags = tags,
            state = state,
            author = author,
            description = description,
            rating = rating,
            chapters = chaptersDeferred.await()
        )
    }

    private suspend fun getChapters(doc: Document): List<MangaChapter> {
        val mangaUrl = doc.location()
        val mangaId = generateUid(mangaUrl.toRelativeUrl(domain))
        
        return doc.select("div.chapter-list a").mapIndexed { index, item ->
            val href = item.attrAsRelativeUrl("href")
            val name = item.text().trim()
            val dateText = item.selectFirst("span.chapter-date")?.text()
            val date = try {
                dateText?.let { dateFormat.parse(it)?.time } ?: 0L
            } catch (e: Exception) {
                0L
            }
            
            MangaChapter(
                id = generateUid(href),
                name = name,
                number = index + 1,
                url = href,
                scanlator = null,
                uploadDate = date,
                branch = null,
                source = source
            )
        }.reversed()
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val fullUrl = chapter.url.toAbsoluteUrl(domain)
        val doc = webClient.httpGet(fullUrl).parseHtml()
        
        return doc.select("div.reader-container img").mapIndexed { i, img ->
            val url = img.attr("data-src").takeIf { it.isNotEmpty() } ?: img.attr("src")
            MangaPage(
                id = generateUid(url),
                url = url,
                preview = null,
                source = source
            )
        }
    }

    override suspend fun fetchAvailableTags(): Set<MangaTag> {
        val doc = webClient.httpGet("https://$domain/tags").parseHtml()
        return doc.select("div.tags-item").mapToSet { element ->
            val tagName = element.selectFirst("div.tags-name")?.text() ?: return@mapToSet null
            val tagHref = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapToSet null
            val tagKey = tagHref.substringAfterLast("=")
            MangaTag(title = tagName, key = tagKey, source = source)
        }
    }
} 