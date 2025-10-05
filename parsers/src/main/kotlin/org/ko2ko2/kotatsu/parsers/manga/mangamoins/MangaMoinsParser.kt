package org.koitharu.kotatsu.parsers.manga.mangamoins

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MANGAMOINS", "MangaMoins", "mangamoins.shaeishu.co", type = ContentType.MANGA, languages = [Language.FRENCH])
internal class MangaMoinsParser(context: MangaLoaderContext) :
    PagedMangaParser(context, MangaSource.MANGAMOINS, 20) {

    override val configKeyDomain = ConfigKey.Domain("mangamoins.shaeishu.co")

    override suspend fun getPopularManga(page: Int): PagedMangaList {
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
                    rating = 0f,
                    isNsfw = false,
                    coverUrl = coverUrl,
                    tags = emptySet(),
                    state = MangaState.ONGOING,
                    author = author,
                    source = source
                )
            )
        }

        val hasNext = doc.select("div.bottom_pages a.active").firstOrNull()?.nextElementSibling() != null
        return PagedMangaList(mangas, page, hasNext)
    }

    override suspend fun search(query: String): List<Manga> {
        return emptyList()
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.publicUrl).parseHtml()
        val fullTitle = doc.selectFirst("title")?.text()?.removePrefix("MangaMoins | ") ?: manga.title
        val cleanTitle = fullTitle.substringBefore("#").trim()

        return manga.copy(
            title = cleanTitle,
            chapters = listOf(
                MangaChapter(
                    id = manga.id,
                    name = manga.title,
                    url = manga.url,
                    publicUrl = manga.publicUrl,
                    uploadDate = 0L,
                    source = source,
                    scanlator = "MangaMoins"
                )
            )
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.publicUrl).parseHtml()
        val pages = mutableListOf<MangaPage>()

        val imageLinks = doc.select("link[rel=preload][as=image]")
        imageLinks.forEachIndexed { index, el ->
            val url = el.attr("href").toAbsoluteUrl(domain)
            pages.add(
                MangaPage(
                    id = index.toLong(),
                    url = url,
                    preview = null,
                    source = source
                )
            )
        }

        return pages
    }

    override fun getRequestHeaders() = org.jsoup.Connection.Headers().apply {
        add("User-Agent", UserAgents.CHROME_DESKTOP)
        add("Referer", "https://mangamoins.shaeishu.co/")
    }
}
