package org.koitharu.kotatsu.parsers.site.tr

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("ELECEEDTURKIYE", "Eleceed Türkiye", "tr")
internal class EleceedTurkiye(context: MangaLoaderContext) : MangaParser(context, MangaSource.ELECEEDTURKIYE) {

    // Domain yapılandırması
    override val configKeyDomain = ConfigKey.Domain(
        "eleceedturkiye.com",
        "www.eleceedturkiye.com"
    )

    // Sadece tek manga olduğu için sabit manga ID
    private val mangaId = "eleceed"
    
    // Türkçe tarih formatı
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr"))

    override suspend fun getDetails(manga: Manga): Manga {
        // Ana manga sayfasını çek
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
        
        return manga.copy(
            description = doc.selectFirst("div.entry-content")?.html(),
            state = MangaState.ONGOING,
            author = "Son Jae Ho & ZHENA",
            tags = setOf(
                MangaTag("aksiyon", "Aksiyon", source),
                MangaTag("drama", "Drama", source),
                MangaTag("komedi", "Komedi", source),
                MangaTag("süper-güçler", "Süper Güçler", source)
            ),
            chapters = doc.select("div.episode-list > ul > li > a").map { a ->
                val href = a.attrAsRelativeUrl("href")
                
                MangaChapter(
                    id = generateUid(href),
                    name = a.selectFirst("span.episode-name")?.text() 
                        ?: "Bölüm ${parseChapterNumberFromUrl(href)}",
                    number = parseChapterNumberFromUrl(href).toFloat(),
                    url = href,
                    uploadDate = parseChapterDate(a.selectFirst("span.episode-date")?.text()),
                    scanlator = null,
                    branch = null,
                    source = source,
                )
            }.sortedByDescending { it.number }
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
        
        return doc.select("div.reading-content > img").map { img ->
            val src = img.attr("src").ifEmpty { img.attr("data-src") }
            MangaPage(
                id = generateUid(src),
                url = src.toAbsoluteUrl(domain),
                preview = null,
                source = source,
            )
        }
    }

    override suspend fun getList(offset: Int, query: String?): List<Manga> {
        // Sadece tek manga olduğu için sabit liste döndürüyoruz
        return listOf(getMangaDetails())
    }

    private fun getMangaDetails(): Manga {
        return Manga(
            id = generateUid(mangaId),
            title = "Eleceed",
            altTitle = null,
            url = "/eleeced-bolum-$mangaId",
            publicUrl = "https://$domain/eleceed/$mangaId",
            rating = RATING_UNKNOWN,
            isNsfw = false,
            coverUrl = "https://$domain/wp-content/uploads/eleceed-cover.jpg",
            largeCoverUrl = null,
            state = MangaState.ONGOING,
            author = "Son Jae Ho & ZHENA",
            source = source,
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

    private fun parseChapterNumberFromUrl(url: String): Int {
        return try {
            url.substringAfterLast('-')
                .substringBefore('/')
                .toInt()
        } catch (e: Exception) {
            url.hashCode().absoluteValue
        }
    }
}
