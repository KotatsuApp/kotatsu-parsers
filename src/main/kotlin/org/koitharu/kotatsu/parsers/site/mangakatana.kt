package org.koitharu.kotatsu.parsers.site.mangakatana

import org.koitharu.kotatsu.parsers.*
import org.koitharu.kotatsu.parsers.annotation.MangaSourceParser
import org.koitharu.kotatsu.model.*

@MangaSourceParser(
    name = "mangakatana",
    title = "MangaKatana",
    lang = "en"
)
class MangaKatanaParser(context: MangaLoaderContext) : MangaParser(context) {
    // site domain
    override val configKeyDomain: String = "mangakatana.com"
    override val availableSortOrders: Set<MangaSortOrder> = setOf(MangaSortOrder.Popular, MangaSortOrder.Newest)

    // Manga list parsing (adapt the URL and selectors as per site's structure)
    override fun getMangaList(
        page: Int,
        sortOrder: MangaSortOrder,
        query: String?,
        filters: List<Filter>
    ): List<Manga> {
        val url = domain("/genre-all?page=$page")
        val document = http.getHtml(url)

        // Inspect site for appropriate selectors for manga blocks
        val elements = document.select("div.item") // example selector
        return elements.map {
            val linkElement = it.selectFirst("a")
            Manga(
                id = generateUid(domain, linkElement!!.attr("href")),
                title = linkElement.attr("title"),
                thumbnailUrl = it.selectFirst("img")!!.absUrl("src")
            )
        }
    }

    // Manga detail parsing
    override fun getMangaDetails(mangaId: String): MangaDetails {
        val document = http.getHtml(domain(mangaId))
        return MangaDetails(
            id = mangaId,
            title = document.selectFirst("div.panel-info-top h1")!!.text(),
            author = document.selectFirst("div.author")?.text(),
            description = document.selectFirst("div.story")?.text() ?: "",
            // Add genres, status, etc., if available
        )
    }

    // Chapter list parsing
    override fun getChapters(mangaId: String): List<Chapter> {
        val document = http.getHtml(domain(mangaId))
        return document.select("div.chapter-list a").map {
            Chapter(
                id = generateUid(domain, it.attr("href")),
                title = it.text(),
                number = extractChapterNumber(it.text())
            )
        }
    }

    // Page parsing (images for each chapter)
    override fun getPages(chapterId: String): List<Page> {
        val document = http.getHtml(domain(chapterId))
        return document.select("div.read-page img").mapIndexed { i, img ->
            Page(i, img.absUrl("src"))
        }
    }

    private fun extractChapterNumber(title: String): Float {
        // Basic chapter number extraction, can be improved
        val regex = Regex("""Chapter (\d+(\.\d+)?)""")
        return regex.find(title)?.groups?.get(1)?.value?.toFloatOrNull() ?: 0f
    }
}
