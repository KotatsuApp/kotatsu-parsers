package org.koitharu.kotatsu.parsers.site.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.SinglePageMangaParser
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("MANGAMOINS", "MangaMoins", "fr")
internal class MangaMoins(context: MangaLoaderContext) :
    SinglePageMangaParser(context, MangaParserSource.MANGAMOINS) {

    override val configKeyDomain = ConfigKey.Domain("mangamoins.com")
    override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)
    override val filterCapabilities = MangaListFilterCapabilities()

    override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()

    override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
        return listOf(
            Manga(
                id = generateUid("OP"),
                title = "One Piece",
                altTitles = emptySet(),
                url = "OP",
                publicUrl = "https://mangamoins.com/",
                rating = RATING_UNKNOWN,
                contentRating = null,
                coverUrl = "https://mangamoins.com/files/scans/OP1161/thumbnail.png",
                tags = emptySet(),
                state = MangaState.ONGOING,
                authors = setOf("Eiichiro Oda"),
                source = source,
            ),
            Manga(
                id = generateUid("LCDL"),
                title = "Les Carnets de l'Apothicaire",
                altTitles = emptySet(),
                url = "LCDL",
                publicUrl = "https://mangamoins.com/",
                rating = RATING_UNKNOWN,
                contentRating = null,
                coverUrl = "https://mangamoins.com/files/scans/LCDL76.2/thumbnail.png",
                tags = emptySet(),
                state = MangaState.ONGOING,
                authors = setOf("Itsuki Nanao", "Nekokurage"),
                source = source,
            ),
            Manga(
                id = generateUid("JKM"),
                title = "Jujutsu Kaisen Modulo",
                altTitles = emptySet(),
                url = "JKM",
                publicUrl = "https://mangamoins.com/",
                rating = RATING_UNKNOWN,
                contentRating = null,
                coverUrl = "https://mangamoins.com/files/scans/JKM1/thumbnail.png",
                tags = emptySet(),
                state = MangaState.ONGOING,
                authors = setOf("Gege Akutami", "Yuji Iwasaki"),
                source = source,
            ),
            Manga(
                id = generateUid("OPC"),
                title = "One Piece Colo",
                altTitles = emptySet(),
                url = "OPC",
                publicUrl = "https://mangamoins.com/",
                rating = RATING_UNKNOWN,
                contentRating = null,
                coverUrl = "https://mangamoins.com/files/scans/OPC1160/thumbnail.png",
                tags = emptySet(),
                state = MangaState.ONGOING,
                authors = setOf("Eiichiro Oda"),
                source = source,
            ),
            Manga(
                id = generateUid("LDS"),
                title = "L'Atelier des Sorciers",
                altTitles = emptySet(),
                url = "LDS",
                publicUrl = "https://mangamoins.com/",
                rating = RATING_UNKNOWN,
                contentRating = null,
                coverUrl = "https://sceneario.com/wp-content/uploads/2023/05/9782811641344-1.jpg",
                tags = emptySet(),
                state = MangaState.ONGOING,
                authors = setOf("Kamome Shirahama"),
                source = source,
            )
        )
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet("https://$domain").parseHtml()
        val prefix = manga.url
        val latestSiteChapterNumber = doc.select("div.sortie a").mapNotNull { a ->
            val href = a.attr("href")
            if (href.startsWith("?scan=$prefix")) {
                href.substringAfter(prefix).toFloatOrNull()
            } else {
                null
            }
        }.maxOrNull()

        if (latestSiteChapterNumber == null) return manga

        val cachedChapters = manga.chapters
        val lastKnownChapterNumber = cachedChapters?.maxByOrNull { it.number }?.number

        if (lastKnownChapterNumber != null && cachedChapters.isNotEmpty()) {
            // INCREMENTAL SCAN (CACHE EXISTS)
            if (latestSiteChapterNumber <= lastKnownChapterNumber) {
                return manga.copy(chapters = cachedChapters.sortedBy { it.number })
            }

            val newChapters = mutableListOf<MangaChapter>()
            var currentChapterInt = latestSiteChapterNumber.toInt()

            while (currentChapterInt > lastKnownChapterNumber.toInt()) {
                val chaptersForGroup = doChecks(prefix, currentChapterInt)
                newChapters.addAll(chaptersForGroup)
                currentChapterInt--
            }

            val combinedChapters = (cachedChapters + newChapters).distinctBy { it.number }
            return manga.copy(chapters = combinedChapters.sortedBy { it.number })

        } else {
            // FULL SCAN (NO CACHE)
            val allChapters = mutableListOf<MangaChapter>()
            var currentChapterInt = latestSiteChapterNumber.toInt()
            var misses = 0

            while (currentChapterInt >= 1 && misses < 4) {
                val chaptersForGroup = doChecks(prefix, currentChapterInt)
                if (chaptersForGroup.isNotEmpty()) {
                    misses = 0
                    allChapters.addAll(chaptersForGroup)
                } else {
                    misses++
                }
                currentChapterInt--
            }
            return manga.copy(chapters = allChapters.sortedBy { it.number })
        }
    }

    private suspend fun doChecks(prefix: String, chapterInt: Int): List<MangaChapter> {
        val foundChapters = mutableListOf<MangaChapter>()

        // Check for integer chapter
        val integerChapter = checkChapter(prefix, chapterInt.toString(), chapterInt.toFloat())
        if (integerChapter != null) {
            foundChapters.add(integerChapter)
        }

        // Conditionally and sequentially check for decimal chapters
        if (prefix == "LCDL") {
            // Start from 2 because .1 never exists
            for (i in 2..9) {
                val decimalNum = chapterInt + (i / 10.0f)
                val decimalNumStr = String.format(Locale.US, "%.1f", decimalNum)
                val decimalChapter = checkChapter(prefix, decimalNumStr, decimalNum)
                if (decimalChapter != null) {
                    foundChapters.add(decimalChapter)
                } else {
                    break // Stop if there's a gap
                }
            }
        }
        return foundChapters
    }

    private suspend fun checkChapter(prefix: String, chapterNumStr: String, chapterNumFloat: Float): MangaChapter? {
        val thumbUrl = "https://mangamoins.com/files/scans/$prefix$chapterNumStr/thumbnail.png"
        return try {
            val response = webClient.httpHead(thumbUrl)
            if (response.isSuccessful) {
                response.close()
                val chapterUrl = "https://mangamoins.com/?scan=$prefix$chapterNumStr"
                MangaChapter(
                    id = generateUid(chapterUrl),
                    title = null,
                    number = chapterNumFloat,
                    volume = 0,
                    url = chapterUrl,
                    scanlator = null,
                    uploadDate = 0,
                    branch = null,
                    source = source
                )
            } else {
                response.close()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.url).parseHtml()
        return doc.select("link[rel=preload][as=image]").map { element ->
            val url = element.attr("href").toAbsoluteUrl(domain)
            MangaPage(
                id = generateUid(url),
                url = url,
                preview = null,
                source = source
            )
        }
    }
}
