package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.Broken

@Broken
@MangaSourceParser("DOCTRUYEN3Q", "DocTruyen3Q", "vi")
internal class DocTruyen3Q(context: MangaLoaderContext) :
    WpComicsParser(context, MangaParserSource.DOCTRUYEN3Q, "doctruyen3qmoi.pro", 36) {

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}
	
    override val datePattern = "dd/MM/yyyy"

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = buildString {
            append("https://")
            append(domain)
            append("/tim-truyen")
            if (filter.query?.isNotEmpty() == true) {
                append("?keyword=")
                append(filter.query.urlEncoded())
            }
            if (page > 1) {
                append(if (filter.query?.isNotEmpty() == true) "&" else "?")
                append("page=")
                append(page.toString())
            }
        }
        val doc = webClient.httpGet(url).parseHtml()
        return parseMangaList(doc)
    }

    private fun parseMangaList(doc: Document): List<Manga> {
        return doc.select("div.list-story-item").map { div ->
            val href = div.selectFirst("h3.title-book a")?.attrAsRelativeUrl("href") ?: div.parseFailed("Manga link not found")
            Manga(
                id = generateUid(href),
                url = href,
                publicUrl = href.toAbsoluteUrl(div.host ?: domain),
                coverUrl = div.selectFirst("div.image img")?.src().orEmpty(),
                title = div.selectFirst("h3.title-book a")?.text().orEmpty(),
                altTitle = null,
                rating = RATING_UNKNOWN,
                tags = emptySet(),
                author = null,
                state = null,
                source = source,
                isNsfw = isNsfwSource,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
        return manga.copy(
            tags = doc.select("li.kind a").mapNotNullToSet { a ->
                MangaTag(
                    key = a.attr("href").substringAfterLast('/'),
                    title = a.text().trim(),
                    source = source,
                )
            },
            author = doc.selectFirst("li.author")?.text()?.substringAfter(':')?.trim(),
            description = doc.selectFirst("div.detail-content p")?.html(),
            state = when (doc.selectFirst("li.status")?.text()?.substringAfter(':')?.trim()) {
                "Đang tiến hành" -> MangaState.ONGOING
                "Đã hoàn thành" -> MangaState.FINISHED
                else -> null
            },
            chapters = getChapters(doc),
        )
    }

    override suspend fun getChapters(doc: Document): List<MangaChapter> {
        val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
        return doc.select("div.list-chapter > a").mapChapters(reversed = true) { i, a ->
            val href = a.attrAsRelativeUrl("href")
            val dateText = a.selectFirst("span.chapter-time")?.text()
            MangaChapter(
                id = generateUid(href),
                name = a.selectFirst("span.chapter-text")?.text() ?: "Chapter ${i + 1}",
                number = i + 1f,
                url = href,
                uploadDate = parseChapterDate(
                    dateFormat,
                    dateText,
                ),
                source = source,
                scanlator = null,
                branch = null,
                volume = 0,
            )
        }
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val fullUrl = chapter.url.toAbsoluteUrl(domain)
        val doc = webClient.httpGet(fullUrl).parseHtml()
        return doc.select("div.page-chapter img").map { img ->
            val url = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
            MangaPage(
                id = generateUid(url),
                url = url,
                preview = null,
                source = source,
            )
        }
    }
}
