package org.koitharu.kotatsu.parsers.site.mangakatana

import org.koitharu.kotatsu.parsers.*
import org.koitharu.kotatsu.parsers.annotation.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.jsoup.nodes.Element

@MangaSourceParser(
    name = "mangakatana",
    title = "MangaKatana",
    lang = "en"
)
class MangaKatanaParser(context: MangaLoaderContext) : PagedMangaParser(context) {
    override val configKeyDomain: String = "mangakatana.com"
    override val availableSortOrders: Set<MangaSortOrder> = setOf(MangaSortOrder.Popular)

    override fun getMangaList(page: Int, sortOrder: MangaSortOrder, query: String?, filters: List<Filter>): List<Manga> {
        val url = domain("/genre-all?page=$page")
        val document = http.getHtml(url)
        val elements = document.select("div.item")
        return elements.map { item: Element ->
            val link = item.selectFirst("a")
            Manga(
                id = generateUid(link!!.attr("href")),
                title = link.attr("title"),
                thumbnailUrl = item.selectFirst("img")!!.absUrl("src")
            )
        }
    }

    override fun getMangaDetails(mangaId: String): MangaDetails {
        val document = http.getHtml(domain(mangaId))
        return MangaDetails(
            id = mangaId,
            title = document.selectFirst("div.panel-info-top h1")?.text() ?: "",
            author = document.selectFirst("div.author")?.text(),
            description = document.selectFirst("div.story")?.text() ?: ""
            // Add genres, status, etc., as needed
        )
    }

    override fun getChapters(mangaId: String): List<Chapter> {
        val document = http.getHtml(domain(mangaId))
        return document.select("div.chapter-list a").map { link: Element ->
            Chapter(
                id = generateUid(link.attr("href")),
                title = link.text(),
                number = link.text().substringAfter("Chapter ").toFloatOrNull() ?: 0f
            )
        }
    }

    override fun getPages(chapterId: String): List<MangaPage> {
        val document = http.getHtml(domain(chapterId))
        return document.select("div.read-page img").mapIndexed { i, img: Element ->
            MangaPage(
                id = generateUid(img.absUrl("src")),
                url = img.absUrl("src"),
                preview = null,
                source = source
            )
        }
    }
}
