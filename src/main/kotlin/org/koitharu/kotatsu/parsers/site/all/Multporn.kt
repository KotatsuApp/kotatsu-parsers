package org.koitharu.kotatsu.parsers.site.all

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MULTPORN", "Multporn")
internal class Multporn(context: MangaLoaderContext) :
    LegacyPagedMangaParser(context, MangaParserSource.MULTPORN, 42) {

    override val configKeyDomain = ConfigKey.Domain("multporn.net")

    override fun getRequestHeaders(): Headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.122 Safari/537.36")
		.add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
		.build()

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.NEWEST,
        SortOrder.NEWEST_ASC,
        SortOrder.UPDATED,
        SortOrder.UPDATED_ASC,
    )

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
        )
    
    init {
		setFirstPage(0)
	}

    override suspend fun getFilterOptions() = MangaListFilterOptions(
        availableLocales = setOf(
            Locale("en"),
            Locale("de"),
            Locale("ru"),
            Locale("zh"),
            Locale("es"),
        ),
        availableContentTypes = EnumSet.of(
			ContentType.COMICS,
            ContentType.HENTAI,
		),
    )

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = buildString {
            append("https://")
            append(domain)
            when {
                !filter.query.isNullOrEmpty() -> {
                    append("/search?search_api_views_fulltext=")
                    val encodedQuery = filter.query.splitByWhitespace().joinToString(separator = "+") { part ->
					    part.urlEncoded()
				    }
                    append(encodedQuery)
                    append("&undefined=Search")
                    append("&page=$page")
                }

                filter.tags.isNotEmpty() -> {
                    val tag = filter.tags.first()
                    append("/category/")
                    append(tag.key)

                    append("?sort_by=")
                    append(
						when (order) {
							SortOrder.NEWEST -> "created"
                            else -> "title" // default
						}
					)

                    append("&page=0,")
                    append(page)
                }
                
                else -> {
                    append("/new")
                    append("?type=")
                    if (filter.types.isNotEmpty()) {
						filter.types.oneOrThrowIfMany()?.let {
							append(
								when (it) {
									ContentType.COMICS -> "1"
                                    ContentType.HENTAI -> "2"
									else -> "All" // all
								},
							)
						}
					} else append("All")

                    
                    filter.locale?.let {
                        append("&language=")
                        append(
                            when (it) {
                                Locale("en") -> "1"
                                Locale("de") -> "2"
                                Locale("ru") -> "3"
                                Locale("zh") -> "4"
                                Locale("es") -> "5"
                                else -> "All"
                            }
                        )
                    }

                    append("&field_user_discription_value=All")
                    
                    append("&sort_by=")
					append(
						when (order) {
							SortOrder.NEWEST -> "created&sort_order=DESC"
							SortOrder.NEWEST_ASC -> "created&sort_order=ASC"
							SortOrder.UPDATED -> "changed&sort_order=DESC"
							SortOrder.UPDATED_ASC -> "changed&sort_order=ASC"
                            else -> "created&sort_order=DESC" // default
						}
					)
                    
                    append("&undefined=Apply")
                    append("&page=$page")
                }
            }
        }

        val doc = webClient.httpGet(url).parseHtml()
        return doc.select(".masonry-item").map { div ->
            val href = div.selectFirstOrThrow(".views-field-title a").attrAsRelativeUrl("href")
            val coverUrl = div.selectFirstOrThrow(".views-field img").requireSrc()
            Manga(
                id = generateUid(href),
                title = div.select(".views-field-title").text(),
                altTitles = emptySet(),
                url = href,
                publicUrl = href.toAbsoluteUrl(domain),
                rating = RATING_UNKNOWN,
                contentRating = ContentRating.ADULT,
                coverUrl = coverUrl,
                tags = emptySet(),
                state = null,
                authors = emptySet(),
                source = source,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
        val authors = (doc.select(".field:has(.field-label:contains(Author:)) .links a").map { it.text() } +
                parseUnlabelledAuthorNames(doc)).distinct()

        val tags = listOf("Tags", "Section", "Characters")
            .flatMap { type -> 
                doc.select(".field:has(.field-label:contains($type:)) .links a").map { it.text() }
            }
            .distinct()
            .map { tag ->
                MangaTag(
                    title = tag,
                    key = tag.lowercase().replace(" ", "_"),
                    source = source,
                )
            }.toSet()

        val isOngoing = doc.select(".field .links a").any { it.text() == "Ongoings" }

        return manga.copy(
            authors = authors.toSet(),
            tags = tags,
            description = buildString {
                append("Pages: ")
                append(doc.select(".jb-image img").size)
                append("\n\n")
                doc.select(".field:has(.field-label:contains(Section:)) .links a").joinTo(this, prefix = "Section: ") { it.text() }
                doc.select(".field:has(.field-label:contains(Characters:)) .links a").joinTo(this, prefix = "\n\nCharacters: ") { it.text() }
            },
            state = if (isOngoing) MangaState.ONGOING else MangaState.FINISHED,
            chapters = listOf(
                MangaChapter(
                    id = generateUid(manga.url),
                    title = "Oneshot",
                    number = 1f,
                    volume = 0,
                    url = manga.url,
                    scanlator = null,
                    uploadDate = 0L,
                    branch = null,
                    source = source,
                )
            ),
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
        return doc.select(".jb-image img").mapIndexed { i, img ->
            val url = img.attrAsAbsoluteUrl("src")
                .replace("/styles/juicebox_2k/public", "")
                .substringBefore("?")
            MangaPage(
                id = generateUid(url),
                url = url,
                preview = null,
                source = source,
            )
        }
    }

    private fun parseUnlabelledAuthorNames(document: org.jsoup.nodes.Document): List<String> {
        val authorClasses = listOf(
            "field-name-field-author",
            "field-name-field-authors-gr",
            "field-name-field-img-group",
            "field-name-field-hentai-img-group",
            "field-name-field-rule-63-section"
        )
        return authorClasses.flatMap { className ->
            document.select(".$className a").map { it.text().trim() }
        }
    }
}