package org.koitharu.kotatsu.parsers.site.madara

import androidx.collection.arraySetOf
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

abstract class Madara5Parser @InternalParsersApi constructor(
    context: MangaLoaderContext,
    source: MangaSource,
    domain: String,
) : PagedMangaParser(context, source, pageSize = 22) {

    protected open val datePattern = "MMMM dd, HH:mm"
    protected open val tagPrefix = "/mangas/"
    protected open val nsfwTags = arraySetOf("yaoi", "yuri", "mature")

    override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

    override val configKeyDomain = ConfigKey.Domain(domain, null)

    override suspend fun getListPage(
        page: Int,
        query: String?,
        tags: Set<MangaTag>?,
        sortOrder: SortOrder,
    ): List<Manga> {
        val domain = domain
        val url = buildString {
            append("https://")
            append(domain)
            append("/search?s=")
            if (!query.isNullOrEmpty()) {
                append(query.urlEncoded())
            }
            append("&post_type=wp-manga")
            if (!tags.isNullOrEmpty()) {
                for (tag in tags) {
                    append("&genre%5B%5D=")
                    append(tag.key)
                }
            }
            append("&op=1&author=&artist=&page=")
            append(page)
        }
        val root = webClient.httpGet(url).parseHtml().body().selectFirstOrThrow(".search-wrap")
        return root.select(".c-tabs-item__content").map { div ->
            val a = div.selectFirstOrThrow("a")
            val img = div.selectLastOrThrow("img")
            val href = a.attrAsRelativeUrl("href")
            val postContent = root.selectFirstOrThrow(".post-content")
            val tagSet = postContent.getElementsContainingOwnText("Genre")
                .firstOrNull()?.tableValue()
                ?.getElementsByAttributeValueContaining("href", tagPrefix)
                ?.mapToSet { it.asMangaTag() }.orEmpty()
            Manga(
                id = generateUid(href),
                title = a.attr("title"),
                altTitle = postContent.getElementsContainingOwnText("Alternative")
                    .firstOrNull()?.tableValue()?.text()?.trim(),
                url = href,
                publicUrl = a.attrAsAbsoluteUrl("href"),
                coverUrl = img.src().orEmpty(),
                author = postContent.getElementsContainingOwnText("Author")
                    .firstOrNull()?.tableValue()?.text()?.trim(),
                state = postContent.getElementsContainingOwnText("Status")
                    .firstOrNull()?.tableValue()?.text()?.asMangaState(),
                isNsfw = isNsfw(tagSet),
                rating = div.selectFirstOrThrow(".score").text()
                    .toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
                tags = tagSet,
                source = source,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml().body()
            .selectFirstOrThrow(".site-content")
        val postContent = root.selectFirstOrThrow(".post-content")
        val tags = postContent.getElementsContainingOwnText("Genre")
            .firstOrNull()?.tableValue()
            ?.getElementsByAttributeValueContaining("href", tagPrefix)
            ?.mapToSet { a -> a.asMangaTag() } ?: manga.tags
        val mangaId = root.getElementById("manga-chapters-holder")?.attr("data-id")?.toLongOrNull()
            ?: root.parseFailed("Cannot find mangaId")
        return manga.copy(
            description = (root.selectFirst(".detail-content")
                ?: root.selectFirstOrThrow(".description-summary")).html(),
            author = postContent.getElementsContainingOwnText("Author")
                .firstOrNull()?.tableValue()?.text()?.trim(),
            state = postContent.getElementsContainingOwnText("Status")
                .firstOrNull()?.tableValue()?.text()?.asMangaState(),
            tags = tags,
            isNsfw = isNsfw(tags),
            chapters = loadChapters(mangaId),
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val fullUrl = chapter.url.toAbsoluteUrl(domain)
        val doc = webClient.httpGet(fullUrl).parseHtml()
        val arrayData = doc.getElementById("arraydata") ?: doc.parseFailed("#arraydata not found")
        return arrayData.html().split(',').map { url ->
            MangaPage(
                id = generateUid(url),
                url = url,
                referer = fullUrl,
                preview = null,
                source = source,
            )
        }
    }

    override suspend fun getTags(): Set<MangaTag> {
        val doc = webClient.httpGet("http://${domain}/").parseHtml().body()
        return doc.getElementsByAttributeValueContaining("href", tagPrefix)
            .mapToSet { it.asMangaTag() }
    }

    private suspend fun loadChapters(mangaId: Long): List<MangaChapter> {
        val dateFormat = SimpleDateFormat(datePattern, sourceLocale ?: Locale.US)
        val doc = webClient.httpGet("https://${domain}/ajax-list-chapter?mangaID=$mangaId").parseHtml()
        return doc.select("li.wp-manga-chapter").asReversed().mapChapters { i, li ->
            val a = li.selectFirstOrThrow("a")
            val href = a.attrAsRelativeUrl("href")
            MangaChapter(
                id = generateUid(href),
                url = href,
                name = a.text(),
                number = i + 1,
                branch = null,
                uploadDate = dateFormat.tryParse(
                    li.selectFirst(".chapter-release-date")?.text()?.trim(),
                ),
                scanlator = null,
                source = source,
            )
        }
    }

    protected fun isNsfw(tags: Set<MangaTag>): Boolean {
        return tags.any { it.key in nsfwTags }
    }

    private fun Element.src(): String? {
        return absUrl("data-src").ifEmpty {
            absUrl("src")
        }.takeUnless { it.isEmpty() }
    }

    private fun Element.tableValue(): Element {
        for (p in parents()) {
            val children = p.children()
            if (children.size == 2) {
                return children[1]
            }
        }
        parseFailed("Cannot find tableValue for node ${text()}")
    }

    private fun String.asMangaState() = when (trim().lowercase(sourceLocale ?: Locale.US)) {
        "ongoing" -> MangaState.ONGOING
        "completed" -> MangaState.FINISHED
        else -> null
    }

    private fun Element.asMangaTag() = MangaTag(
        title = ownText(),
        key = attr("href").removeSuffix('/').substringAfterLast('/')
            .replace('-', '+'),
        source = source,
    )

    @MangaSourceParser("MANGAOWLS", "BeautyManga", "en")
    class BeautyManga(context: MangaLoaderContext) : Madara5Parser(context, MangaSource.MANGAOWLS, "beautymanga.com") {

    }
}
