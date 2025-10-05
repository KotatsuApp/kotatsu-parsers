package org.ko2ko2.kotatsu.parsers.manga.mangamoins

import org.ko2ko2.kotatsu.parsers.common.*
import org.ko2ko2.kotatsu.parsers.extension.*
import org.ko2ko2.kotatsu.parsers.model.*
import org.ko2ko2.kotatsu.parsers.source.Language
import org.ko2ko2.kotatsu.parsers.source.MangaSource
import org.ko2ko2.kotatsu.parsers.source.PagedMangaSource
import org.ko2ko2.kotatsu.parsers.utils.*

@MangaSourceParser("MangaMoins", "mangamoins.shaeishu.co", type = ContentType.MANGA, languages = [Language.FRENCH])
class MangaMoinsParser : HtmlMangaParser() {

    override val config = MangaParserConfiguration(
        sourceDomain = "https://mangamoins.shaeishu.co",
        imageBaseUrl = "https://mangamoins.shaeishu.co",
        isNsfw = false,
        isRtl = false,
        headers = mapOf(
            "User-Agent" to UserAgents.CHROME_DESKTOP,
            "Referer" to "https://mangamoins.shaeishu.co/"
        )
    )

    override suspend fun getPopularManga(page: Int): PagedMangaList {
        val doc = httpClient.get("$sourceUrl?p=$page").parseHtml()
        val mangas = doc.select("div.sortie").map { el ->
            val link = el.selectFirst("a")!!
            val href = link.attr("href")
            val scanId = href.substringAfter("scan=").substringBefore("&").substringBefore("#")
            val titleEl = el.selectFirst("figcaption p")!!
            val title = titleEl.ownText().substringBefore("\n").trim()
            val author = titleEl.selectFirst("span")?.text()?.trim() ?: ""
            val coverUrl = el.selectFirst("img")!!.attrAsAbsoluteUrl("src")

            Manga(
                id = generateUid(scanId),
                title = "$title #$scanId",
                url = href,
                publicUrl = "$sourceUrl$href",
                rating = 0f,
                isNsfw = false,
                coverUrl = coverUrl,
                tags = emptySet(),
                state = MangaState.ONGOING,
                author = author,
                source = source
            )
        }

        val hasNext = doc.select("div.bottom_pages a.active").firstOrNull()?.nextElementSibling() != null
        return PagedMangaList(mangas, page, hasNext)
    }

    override suspend fun search(query: String): List<Manga> {
        return emptyList()
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = httpClient.get(manga.publicUrl).parseHtml()
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
        val doc = httpClient.get(chapter.publicUrl).parseHtml()
        return doc.select("link[rel=preload][as=image]").mapIndexed { index, el ->
            MangaPage(
                id = index.toLong(),
                url = el.attrAsAbsoluteUrl("href"),
                chapterId = chapter.id
            )
        }
    }
}
