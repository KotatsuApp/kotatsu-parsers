package org.koitharu.kotatsu.parsers.site.en.MTL

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities
import org.koitharu.kotatsu.parsers.model.search.SearchCapability
import org.koitharu.kotatsu.parsers.model.search.SearchableField
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import java.text.SimpleDateFormat
import java.util.*

internal abstract class MTLParser(
    context: MangaLoaderContext,
    source: MangaParserSource,
    domain: String
): PagedMangaParser(context, source, 24) {

    override val configKeyDomain = ConfigKey.Domain(domain)

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.UPDATED,
        SortOrder.POPULARITY,
        SortOrder.NEWEST
    )

    override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()

    override val searchQueryCapabilities = MangaSearchQueryCapabilities(
        SearchCapability(
            field = SearchableField.TITLE_NAME,
            criteriaTypes = setOf(Match::class),
            isMultiple = false
        )
    )

    override suspend fun getListPage(query: MangaSearchQuery, page: Int): List<Manga> {
        val url = buildString {
            append("https://")
            append(domain)
            append("/search")
            append("?")
            when (query.order) {
                SortOrder.POPULARITY -> append("sort_by=views")
                SortOrder.UPDATED -> append("sort_by=recent")
                else -> append("sort_by=recent")
            }
            if (page > 1) {
                append("&page=")
                append(page)
            }
            query.criteria.find { it.field == SearchableField.TITLE_NAME }?.let { criteria ->
                when (criteria) {
                    is Match -> {
                        append("&q=")
                        append(criteria.value.toString())
                    }
                    is Include,
                    is Exclude,
                    is Range -> Unit // Not supported for this field
                }
            }
        }

        val doc = webClient.httpGet(url).parseHtml()
        return doc.select("div.grid.grid-cols-1.sm\\:grid-cols-2.lg\\:grid-cols-3.xl\\:grid-cols-4.gap-8.p-6 > div").map { div ->
            val href = div.selectFirst("a")?.attr("href") ?: throw ParseException("Link not found", url)
            Manga(
                id = generateUid(href),
                url = href,
                publicUrl = href.toAbsoluteUrl(domain),
                coverUrl = div.selectFirst("a > div > img")?.src().orEmpty(),
                title = div.selectFirst("div > a > h3")?.text().orEmpty(),
                altTitles = emptySet(),
                rating = RATING_UNKNOWN,
                tags = emptySet(),
                authors = emptySet(),
                state = null,
                source = source,
                contentRating = null,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
        val altTitles = doc.select("p:contains(Alternative Title)").firstOrNull()?.text()
            ?.substringAfter("Alternative Title:")
            ?.removeSurrounding("[", "]")
            ?.split(",")
            ?.map { it.trim().removeSurrounding("'", "'") }
            ?.toSet()
            ?: emptySet()
        
        val description = doc.select("p:contains(Synopsis)").firstOrNull()?.text()
            ?.substringAfter("Synopsis:").orEmpty()
        
        val authors = doc.select("p:contains(Author:)").firstOrNull()?.text()
            ?.substringAfter("Author:")
        
        val state = when (doc.select("p:contains(Status:)").firstOrNull()?.text()?.contains("Ongoing") == true) {
            true -> MangaState.ONGOING
            false -> MangaState.FINISHED
        }

        val chaptersRoot = doc.selectFirst("section.bg-gray-800.rounded-lg.shadow-md.mt-8.p-6") 
            ?: throw ParseException("Chapters not found", manga.url)
            
        val chapters = chaptersRoot.select("ul > li").mapNotNull { li ->
            val link = li.selectFirst("a") ?: return@mapNotNull null
            val href = link.attrAsRelativeUrl("href")
            val title = link.text()
            val number = title.substringAfter("Chapter ").substringBefore(" ").toFloatOrNull() ?: 0f
            
            val dateString = li.select("span.text-gray-400").firstOrNull()?.text()?.trim() ?: ""
            val uploadDate = if (dateString.isNotEmpty()) parseChapterDate(dateString) else 0L

            MangaChapter(
                id = generateUid(href),
                title = title,
                number = number,
                volume = 0,
                url = href,
                scanlator = null,
                uploadDate = uploadDate,
                branch = null,
                source = source
            )
        }.toList()

        return manga.copy(
            description = description,
            authors = setOfNotNull(authors),
            state = state,
            altTitles = altTitles,
            chapters = chapters.reversed()
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
        val jsonText = doc.selectFirst("div#json-data")?.text()
            ?: throw ParseException("JSON data not found", chapter.url)
        
        val imgUrls = Regex(""""img_url":\s*"([^"]+)"""").findAll(jsonText)
            .map { it.groupValues[1] }
            .toList()
        
        return imgUrls.map { imgUrl ->
            val fullUrl = "https://$imgUrl"
            MangaPage(
                id = generateUid(fullUrl),
                url = fullUrl,
                preview = null,
                source = source,
            )
        }
    }

    private fun parseChapterDate(dateString: String): Long {
        val calendar = Calendar.getInstance()
        return when {
            "minute" in dateString -> {
                val minutes = dateString.substringBefore(" minute").toInt()
                calendar.add(Calendar.MINUTE, -minutes)
                calendar.timeInMillis
            }
            "hour" in dateString -> {
                val hours = dateString.substringBefore(" hour").toInt()
                calendar.add(Calendar.HOUR_OF_DAY, -hours)
                calendar.timeInMillis
            }
            "day" in dateString -> {
                val days = dateString.substringBefore(" day").toInt()
                calendar.add(Calendar.DAY_OF_YEAR, -days)
                calendar.timeInMillis
            }
            else -> {
                try {
                    val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    sdf.parse(dateString)?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }
        }
    }
}