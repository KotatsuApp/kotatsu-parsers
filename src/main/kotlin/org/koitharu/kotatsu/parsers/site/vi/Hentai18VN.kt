package org.koitharu.kotatsu.parsers.site.vi

import androidx.collection.arraySetOf
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("HENTAI18VN", "Hentai18VN", "vi", type = ContentType.HENTAI)
internal class Hentai18VN(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.HENTAI18VN, 30) {

	override val configKeyDomain = ConfigKey.Domain("hentai18vn.art")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

    override val filterCapabilities: MangaListFilterCapabilities 
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
            isSearchWithFiltersSupported = false
        )

    override suspend fun getFilterOptions() = MangaListFilterOptions(availableTags = fetchAvailableTags())
    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.NEWEST,
        SortOrder.POPULARITY,
        SortOrder.UPDATED
    )
    
    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        return when {
            !filter.query.isNullOrEmpty() -> {
                if (page > 1) {
                    return emptyList()
                }

                val keyword = filter.query
                val url = "http://$domain/search/html/1"
                val headers = Headers.Builder().add("X-Requested-With", "XMLHttpRequest").build()
                val response = webClient.httpPost(url.toHttpUrl(), payload = "keyword=$keyword", headers).parseHtml()
                parseMangaSearch(response)
            }

            !filter.tags.isNullOrEmpty() -> {
                val tag = filter.tags.first()
                val url = buildString {
                    append("https://")
                    append(domain)
                    append("/the-loai/")
                    append(tag.key)
                    if (page > 1) {
                        append("?page=")
                        append(page)
                    }
                }
                val response = webClient.httpGet(url).parseHtml()
                parseMangaList(response)
            }

            else -> {
                val url = buildString {
                    append("https://")
                    append(domain)
                    append("/")
                    append(
                        when (order) {
                            SortOrder.NEWEST -> "danh-sach/truyen-hentai-moi"
                            SortOrder.POPULARITY -> "danh-sach/truyen-hentai-hot"
                            SortOrder.UPDATED -> "danh-sach/truyen-hentai-hoan-thanh"
                            else -> "danh-sach/truyen-hentai-hay"
                        }
                    )
                    if (page > 1) {
                        append("?page=")
                        append(page)
                    }
                }
                
                val response = webClient.httpGet(url).parseHtml()
                parseMangaList(response)
            }
        }
    }

    private fun parseMangaSearch(doc: Document): List<Manga> {
        return doc.select("a.item").map { a ->
            val href = a.attr("href")
            val mangaInfo = a.selectFirst("img")
            Manga(
                id = generateUid(href),
                url = href,
                publicUrl = href.toAbsoluteUrl(domain),
                title = mangaInfo.attr("alt"),
                altTitle = null,
                author = null,
                tags = emptySet(),
                rating = RATING_UNKNOWN,
                state = null,
                coverUrl = mangaInfo.requireSrc(),
                isNsfw = isNsfwSource,
                source = source
            )
        }
    }

    private fun parseMangaList(doc: Document): List<Manga> {
        return doc.select("div.visual").map { div ->
            val a = div.selectFirst("div.main_text h3.title a")
            val img = div.selectFirst("div.hentai-cover img")
            val mangaUrl = a.attr("href")
            Manga(
                id = generateUid(mangaUrl),
                publicUrl = mangaUrl,
                url = mangaUrl.removePrefix("https://$domain"),
                title = a.text(),
                altTitle = null,
                author = null,
                description = null,
                tags = emptySet(),
                rating = RATING_UNKNOWN,
                state = null,
                coverUrl = img.attr("data-original").takeIf { it.isNotEmpty() } ?: img.attr("src"),
                isNsfw = isNsfwSource,
                source = source,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
        val tags = doc.select("div.hentai-info .line-content a.item-tag")
            .mapNotNull { a -> 
                MangaTag(
                    title = a.text(),
                    key = a.attr("href").substringAfterLast("/"),
                    source = source
                )
            }.toSet()

        val chapters = doc.select("ul#chapter-list li.citem").mapChapters(reversed=true) { i, li ->
            val a = li.selectFirst("a")
            MangaChapter(
                id = generateUid(a.attr("href")),
                name = a.text(),
                number = i + 1f,
                url = a.attr("href").removePrefix("https://$domain"),
                uploadDate = parseChapterDate(li.selectFirst(".time")?.text()),
                source = source,
                scanlator = null,
                branch = null,
                volume = 0
            )
        }

        val altTitle = doc.selectFirst("h2.alternative")?.text()
        val author = doc.selectFirst("div.hentai-info .line:contains(Tác giả) .line-content")?.text()
        val state = when(doc.selectFirst("div.hentai-info .line:contains(Tình trạng) .line-content")?.text()) {
            "Đang cập nhật" -> MangaState.ONGOING
            "Hoàn thành" -> MangaState.FINISHED
            else -> null
        }

        return manga.copy(
            tags = tags,
            author = author,
            altTitle = altTitle,
            state = state,
            chapters = chapters,
            description = doc.select("div.about").text()
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
        return doc.select("div.chapter-content div.item-photo img").mapNotNull { img ->
            val url = img.requireSrc()
            MangaPage(
                id = generateUid(url),
                url = url,
                preview = null,
                source = source
            )
        }
    }

    private suspend fun fetchAvailableTags(): Set<MangaTag> {
        val firstPage = webClient.httpGet("https://$domain/tim-the-loai").parseHtml()
        val lastPage = firstPage.selectFirst("a[aria-label=Last]")
            ?.attr("href")
            ?.substringAfter("page=")
            ?.toIntOrNull() ?: 1

        return (1..lastPage).flatMap { page ->
            val doc = if (page == 1) {
                firstPage
            } else {
                webClient.httpGet("https://$domain/tim-the-loai?page=$page").parseHtml()
            }

            doc.select("ul.list-tags li").mapNotNull { li ->
                val a = li.selectFirst("a") ?: return@mapNotNull null
                val title = a.selectFirst("h3.tag-name")?.text()?.trim() ?: return@mapNotNull null
                val key = a.attr("href").substringAfterLast("/")
                MangaTag( title = title, key = key, source = source )
            }
        }.toSet()
    }

    private fun parseChapterDate(date: String?): Long {
        if (date == null) return 0
        return try {
            val now = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            now.parse(date)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}