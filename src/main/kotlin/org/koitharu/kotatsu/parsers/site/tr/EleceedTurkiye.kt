package org.koitharu.kotatsu.parsers.site.tr

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("ELECEEDTURKIYE", "Eleceed Türkiye", "tr")
internal class EleceedTurkiye(context: MangaLoaderContext) : MangaParser(context, MangaParserSource.ELECEEDTURKIYE) {

    override val configKeyDomain = ConfigKey.Domain("eleceedturkiye.com")
	private val domain = "eleceedturkiye.com"
	private val source = MangaParserSource.ELECEEDTURKIYE
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr"))

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
        return manga.copy(
            description = doc.selectFirst("div.entry-content")?.html(),
            state = MangaState.ONGOING,
            author = "Son Jae Ho & ZHENA",
            tags = setOf(
                MangaTag(
                    key = "aksiyon",
                    title = "Aksiyon",
                    source = source,
                ),
                MangaTag(
                    key = "drama",
                    title = "Drama",
                    source = source,
                ),
                MangaTag(
                    key = "komedi",
                    title = "Komedi",
                    source = source,
                ),
                MangaTag(
                    key = "süper güçler",
                    title = "Süper Güçler",
                    source = source,
                ),
            ),
            chapters = doc.select("div.episode-list > ul > li > a").mapChapters(reversed = true) { i, a ->
                val href = a.attrAsRelativeUrl("href")
                MangaChapter(
                    id = generateUid(href),
                    name = a.selectFirst("span.episode-name")?.text() ?: "Bölüm ${i + 1}",
                    number = i + 1,
                    url = href,
                    uploadDate = parseChapterDate(
                        a.selectFirst("span.episode-date")?.text()
                    ),
                    scanlator = null,
                    branch = null,
                    source = source,
                )
            }
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
        return doc.select("div.reading-content > img").map { img ->
            val url = img.attr("src").toAbsoluteUrl(domain)
            MangaPage(
                id = generateUid(url),
                url = url,
                referer = chapter.url.toAbsoluteUrl(domain),
                preview = null,
                source = source,
            )
        }
    }

    override suspend fun getList(offset: Int, query: String?): List<Manga> {
        // Sitede sadece tek manga (Eleceed) olduğu için sabit döndürüyoruz
        return listOf(
            Manga(
                id = generateUid("eleceed"),
                title = "Eleceed",
                altTitle = null,
                url = "/",
                publicUrl = "https://$domain/",
                rating = RATING_UNKNOWN,
                isNsfw = false,
                coverUrl = "https://$domain/wp-content/uploads/2024/04/eleceed-cover.jpg",
                largeCoverUrl = null,
                state = MangaState.ONGOING,
                author = "Son Jae Ho & ZHENA",
                source = source,
            )
        )
    }

    private fun parseChapterDate(dateString: String?): Long {
        return dateString?.let {
            try {
                dateFormat.parse(it)?.time ?: 0
            } catch (e: Exception) {
                0
            }
        } ?: 0
    }
}
