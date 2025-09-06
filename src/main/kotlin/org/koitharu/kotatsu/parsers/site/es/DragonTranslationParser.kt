package org.koitharu.kotatsu.parsers.site.es

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@Broken // Website closed
@MangaSourceParser("DRAGONTRANSLATION", "Dragon Translation", "es")
internal class DragonTranslationParser(context: MangaLoaderContext) :
    PagedMangaParser(context, MangaParserSource.DRAGONTRANSLATION, 30) {

    override val configKeyDomain = ConfigKey.Domain("dragontranslation.net")

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        super.onCreateConfig(keys)
        keys.add(userAgentKey)
    }

    override fun getRequestHeaders() = super.getRequestHeaders().newBuilder()
        .add("referer", "no-referrer")
        .build()

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
        )

    override suspend fun getFilterOptions() = MangaListFilterOptions(
        availableTags = emptySet(), // cant find any URLs for fetch tags
        availableContentTypes = EnumSet.of(ContentType.MANGA, ContentType.MANHWA, ContentType.MANHUA),
    )

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = buildString {
            append("https://")
            append(domain)
            when {
                !filter.query.isNullOrEmpty() -> {
                    append("/mangas?buscar=")
                    append(filter.query.urlEncoded())
                    append("&page=")
                    append(page.toString())
                }

                else -> {
                    append("/mangas?page=")
                    append(page.toString())

                    val tag = filter.tags.oneOrThrowIfMany()
                    if (filter.tags.isNotEmpty()) {
                        append("&tag=")
                        append(tag?.key.orEmpty())
                    }

                    if (filter.types.isNotEmpty()) {
                        append("&type=")
                        append(
                            when (filter.types.oneOrThrowIfMany()) {
                                ContentType.MANGA -> "manga"
                                ContentType.MANHWA -> "manhwa"
                                ContentType.MANHUA -> "manhua"
                                else -> ""
                            },
                        )
                    }
                }
            }
        }

        val doc = webClient.httpGet(url).parseHtml()
        val row = doc.select("div.row.gy-3").firstOrNull() ?: return emptyList()
        return row.select("article.position-relative.card").mapNotNull { div ->
            val href = div.selectFirst("a.lanzador")?.attrAsRelativeUrlOrNull("href") ?: return@mapNotNull null
            val coverUrl = div.selectFirst("img.card-img-top.wp-post-image.lazy.loaded")?.src().orEmpty()
            Manga(
                id = generateUid(href),
                url = href,
                publicUrl = href,
                coverUrl = coverUrl,
                title = div.selectFirst("h2.card-title.fs-6.entry-title")?.text().orEmpty(),
                altTitles = emptySet(),
                rating = RATING_UNKNOWN,
                tags = emptySet(),
                authors = emptySet(),
                state = null,
                source = source,
                contentRating = if (isNsfwSource) ContentRating.ADULT else null,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
        val statusText = doc.selectFirst("p:contains(Status:)")?.text()
        val status = when {
            statusText?.contains("publishing", ignoreCase = true) == true -> MangaState.ONGOING
            else -> null
        }

        val chapterElements = doc.select("ul.list-group a")
        val totalChapters = chapterElements.size

        val chapters = chapterElements.mapIndexed { index, a ->
            val href = a.attrAsRelativeUrl("href")
            val title = a.text()
            MangaChapter(
                id = generateUid(href),
                title = title,
                number = totalChapters - index.toFloat(),
                volume = 0,
                url = href,
                scanlator = null,
                uploadDate = parseDate(a.selectFirst("span")?.text()),
                branch = null,
                source = source,
            )
        }

        return manga.copy(
            state = status,
            chapters = chapters.reversed(),
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val fullUrl = chapter.url.toAbsoluteUrl(domain)
        val doc = webClient.httpGet(fullUrl).parseHtml()
        return doc.select("div#chapter_imgs img").map { img ->
            val url = img.attr("src")
            MangaPage(
                id = generateUid(url),
                url = url,
                preview = null,
                source = source,
            )
        }
    }

    private fun parseDate(dateText: String?): Long {
        if (dateText == null) return 0

        val number = dateText.filter { it.isDigit() }.toIntOrNull() ?: return 0
        val now = System.currentTimeMillis()

        return when {
            dateText.contains("minutos") -> {
                now - (number * 60 * 1000L)
            }

            dateText.contains("horas") -> {
                now - (number * 60 * 60 * 1000L)
            }

            dateText.contains("días") -> {
                now - (number * 24 * 60 * 60 * 1000L)
            }

            dateText.contains("día") -> {
                now - (number * 24 * 60 * 60 * 1000L)
            }

            dateText.contains("semanas") -> {
                now - (number * 7 * 24 * 60 * 60 * 1000L)
            }

            dateText.contains("meses") -> {
                now - (number * 30 * 24 * 60 * 60 * 1000L)
            }

            dateText.contains("años") -> {
                now - (number * 365 * 24 * 60 * 60 * 1000L)
            }

            else -> 0L
        }
    }
}
