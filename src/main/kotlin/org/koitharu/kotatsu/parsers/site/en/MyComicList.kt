package org.koitharu.kotatsu.parsers.site.en

import androidx.collection.arraySetOf
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MYCOMICLIST", "MyComicList", "en", ContentType.COMICS)
internal class MyComicList(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.MYCOMICLIST, 24) {

    override val configKeyDomain = ConfigKey.Domain("mycomiclist.org")
    
    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        super.onCreateConfig(keys)
        keys.add(userAgentKey)
    }

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.UPDATED,
        SortOrder.POPULARITY,
        SortOrder.NEWEST,
        SortOrder.ALPHABETICAL
    )

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
        )

    override suspend fun getFilterOptions() = MangaListFilterOptions(
        availableTags = fetchAvailableTags(),
        availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED)
    )

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = buildString {
            append("https://")
            append(domain)
            when {
                !filter.query.isNullOrEmpty() -> {
                    append("/comic-search?key=")
                    append(filter.query.urlEncoded())
                }
                filter.tags.isNotEmpty() -> {
                    append("/")
                    append(filter.tags.first().key)
                    append("-comic")
                }
                else -> when (order) {
                    SortOrder.UPDATED -> append("/hot-comic")
                    SortOrder.POPULARITY -> append("/popular-comic") 
                    SortOrder.NEWEST -> append("/new-comic")
                    else -> append("/ongoing-comic")
                }
            }
            if (page > 1) {
                append("?page=")
                append(page)
            }
        }

        val doc = webClient.httpGet(url).parseHtml()
        return doc.select("div.manga-box").map { div ->
            val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
            val img = div.selectFirst("img.lazyload")
            Manga(
                id = generateUid(href),
                url = href,
                publicUrl = href.toAbsoluteUrl(domain),
                title = div.selectFirst("h3 a")?.text().orEmpty(),
                altTitle = null,
                author = null,
                tags = emptySet(),
                rating = RATING_UNKNOWN,
                isNsfw = isNsfwSource,
                coverUrl = img?.attr("data-src").orEmpty(),
                state = null,
                source = source,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
        return manga.copy(
            tags = doc.select("td:contains(Genres:) + td a").mapToSet { a ->
                MangaTag(
                    key = a.attr("href").substringAfterLast('/').substringBefore("-comic"),
                    title = a.text().toTitleCase(sourceLocale),
                    source = source
                )
            },
            author = doc.selectFirst("td:contains(Author:) + td")?.textOrNull(),
            state = when(doc.selectFirst("td:contains(Status:) + td a")?.text()?.lowercase()) {
                "ongoing" -> MangaState.ONGOING
                "completed" -> MangaState.FINISHED
                else -> null
            },
            description = doc.selectFirst("div.manga-desc p.pdesc")?.html(),
            chapters = doc.select("ul.basic-list li").mapChapters(reversed = true) { i, li ->
                val a = li.selectFirst("a.ch-name") ?: return@mapChapters null
                val href = a.attrAsRelativeUrl("href")
                val name = a.text()
                
                MangaChapter(
                    id = generateUid(href),
                    name = name,
                    number = name.substringAfter('#').toFloatOrNull() ?: (i + 1f),
                    url = href,
                    scanlator = null,
                    uploadDate = 0L,
                    branch = null,
                    source = source,
                    volume = 0,
                )
            }
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val fullUrl = chapter.url.toAbsoluteUrl(domain) + "/all"
        val doc = webClient.httpGet(fullUrl).parseHtml()
        
        return doc.select("img.chapter_img.lazyload").mapNotNull { img ->
            val imageUrl = img.attrOrNull("data-src") ?: return@mapNotNull null
            MangaPage(
                id = generateUid(imageUrl),
                url = imageUrl,
                preview = null,
                source = source
            )
        }
    }

    private suspend fun fetchAvailableTags(): Set<MangaTag> {
        val doc = webClient.httpGet("https://$domain").parseHtml()
        return doc.select("div.cr-anime-box.genre-box a.genre-name").mapToSet { a ->
            val href = a.attr("href")
            val key = href.substringAfterLast('/').substringBefore("-comic")
            MangaTag(
                key = key,
                title = a.text().toTitleCase(sourceLocale),
                source = source
            )
        }
    }
}
