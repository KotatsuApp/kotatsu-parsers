package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.ArraySet
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.regex.Pattern
import java.util.*

@MangaSourceParser("MYREADINGMANGA", "MyReadingManga")
internal class MyReadingManga(context: MangaLoaderContext) : LegacyPagedMangaParser(context, MangaParserSource.MYREADINGMANGA, 20) {

    override val configKeyDomain = ConfigKey.Domain("myreadingmanga.info")

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isMultipleTagsSupported = true,
            isTagsExclusionSupported = false,
            isSearchSupported = true,
            isOriginalLocaleSupported = true,
        )
    
    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.UPDATED,
        SortOrder.POPULARITY,
        SortOrder.ALPHABETICAL,
        SortOrder.NEWEST,
    )

    override suspend fun getFilterOptions() = MangaListFilterOptions(
        availableTags = fetchTags(),
        availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
        availableContentRating = EnumSet.of(ContentRating.ADULT),
        availableLocales = setOf(
            Locale.ENGLISH, Locale.JAPANESE, Locale.CHINESE, Locale.GERMAN, Locale.ITALIAN,
            Locale("ru"), Locale("es"), Locale("pt", "BR"), Locale("tr"), Locale("vi"),
            Locale("ar"), Locale("id"), Locale("ko"),
        ),
    )

    private fun getLanguageForFilter(locale: Locale): String {
        return when (locale.language) {
            "en" -> "English"
            "ja" -> "Japanese"
            "zh" -> "Chinese"
            "de" -> "German"
            "it" -> "Italian"
            "ru" -> "Russian"
            "es" -> "Spanish"
            "pt" -> "Portuguese"
            "tr" -> "Turkish"
            "vi" -> "Vietnamese"
            "ar" -> "Arabic"
            "id" -> "Indonesia"
            "ko" -> "Korean"
            else -> "English"
        }
    }

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = buildString {
            append("https://")
            append(domain)
            append("/search/?wpsolr_page=")
            append(page)

            when {
                !filter.query.isNullOrEmpty() -> {
                    append("&wpsolr_q=")
                    append(filter.query.replace(' ', '+'))
                }
                else -> {
                    append("&wpsolr_sort=")
                    when (order) {
                        SortOrder.UPDATED -> append("sort_by_date_desc")
                        SortOrder.POPULARITY -> append("sort_by_relevancy_desc")
                        SortOrder.ALPHABETICAL -> append("sort_by_title_asc")
                        SortOrder.NEWEST -> append("sort_by_date_desc")
                        else -> append("sort_by_random")
                    }
                }
            }

            var paramIndex = 0
            
            filter.locale?.let {
                append("&wpsolr_fq[$paramIndex]=lang_str:")
                append(getLanguageForFilter(it))
                paramIndex++
            }

            if (filter.tags.isNotEmpty()) {
                filter.tags.forEach { tag ->
                    append("&wpsolr_fq[$paramIndex]=")
                    append("genre_str:${tag.key}")
                    paramIndex++
                }
            }

            filter.states.oneOrThrowIfMany()?.let {
                append("&wpsolr_fq[$paramIndex]=status:")
                append(
                    when (it) {
                        MangaState.ONGOING -> "Ongoing"
                        MangaState.FINISHED -> "Completed"
                        else -> "Ongoing"
                    },
                )
                paramIndex++
            }
        }

        val doc = webClient.httpGet(url).parseHtml()
        return parseMangaList(doc)
    }

    private suspend fun parseMangaList(doc: Document): List<Manga> {
        return doc.select("div.results-by-facets div[id*=res]").map { element ->
            val titleElement = element.selectFirst("a") ?: element.parseFailed("No title element found")
            val thumbnailElement = element.selectFirst("img")

            Manga(
                id = generateUid(titleElement.attr("href")),
                title = titleElement.text().replace(titleRegex.toRegex(), "").substringBeforeLast("(").trim(),
                altTitles = emptySet(),
                url = titleElement.attrAsRelativeUrl("href"),
                publicUrl = titleElement.absUrl("href"),
                rating = RATING_UNKNOWN,
                contentRating = ContentRating.ADULT,
                coverUrl = findImageSrc(thumbnailElement),
                tags = emptySet(),
                state = null,
                authors = emptySet(),
                source = source,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
        val title = doc.selectFirst("h1")?.text() ?: manga.title
        val author = title.substringAfter("[").substringBefore("]").trim()
        val genres = doc.select(".entry-header p a[href*=genre], [href*=tag], span.entry-categories a")
            .mapToSet {
                MangaTag(
                    title = it.text().toTitleCase(),
                    key = it.attr("href").substringAfterLast("/").substringBefore("/"),
                    source = source,
                )
            }
        
        val basicDescription = title
        val scanlatedBy = doc.select(".entry-terms:has(a[href*=group])")
            .firstOrNull()?.select("a[href*=group]")
            ?.joinToString(prefix = "Scanlated by: ") { it.text() }
        val extendedDescription = doc.select(".entry-content p:not(p:containsOwn(|)):not(.chapter-class + p)")
            .joinToString("\n") { it.text() }
        val description = listOfNotNull(basicDescription, scanlatedBy, extendedDescription).joinToString("\n").trim()
        
        val state = when (doc.select("a[href*=status]").firstOrNull()?.text()) {
            "Ongoing" -> MangaState.ONGOING
            "Completed" -> MangaState.FINISHED
            else -> null
        }
        
        val chapters = parseChapters(doc)
        
        return manga.copy(
            description = description,
            tags = genres,
            state = state,
            authors = setOfNotNull(author.takeIf { it.isNotEmpty() }),
            chapters = chapters,
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
        
        val images = (doc.select("div.entry-content img") + doc.select("div.separator img[data-src]"))
            .mapNotNull { findImageSrc(it) }
            .distinct()
        
        return images.mapIndexed { i, url ->
            MangaPage(
                id = generateUid(url),
                url = url,
                preview = null,
                source = source,
            )
        }
    }

    private suspend fun fetchTags(): Set<MangaTag> {
        val doc = webClient.httpGet("https://${domain}").parseHtml()
        return doc.select(".tagcloud a[href*=/genre/]").mapToSet {
            MangaTag(
                title = it.text().toTitleCase(),
                key = it.attr("href").substringAfterLast("/").substringBefore("/"),
                source = source,
            )
        }
    }

    private val titleRegex = Pattern.compile("""\[[^]]*]""")
    private val imgRegex = Pattern.compile("""\.(jpg|png|jpeg|webp)""")

    private fun findImageSrc(element: Element?): String? {
        element ?: return null
        
        return when {
            element.hasAttr("data-src") && imgRegex.matcher(element.attr("data-src")).find() -> 
                element.absUrl("data-src")
            element.hasAttr("data-cfsrc") && imgRegex.matcher(element.attr("data-cfsrc")).find() -> 
                element.absUrl("data-cfsrc")
            element.hasAttr("src") && imgRegex.matcher(element.attr("src")).find() -> 
                element.absUrl("src")
            element.hasAttr("data-lazy-src") -> 
                element.absUrl("data-lazy-src")
            else -> null
        }
    }

    private fun parseChapters(document: Document): List<MangaChapter> {
        val chapters = mutableListOf<MangaChapter>()
        val mangaUrl = document.baseUri()
        val date = parseDate(document.select(".entry-time").text())
        val chFirstName = document.select(".chapter-class a[href*=$mangaUrl]").firstOrNull()?.text()
            ?.ifEmpty { "Ch. 1" }?.replaceFirstChar { it.uppercase() } ?: "Ch. 1"
        
        chapters.add(importChapter("1", mangaUrl, date, chFirstName))
        
        val lastChapterNumber = document.select("a[class=page-numbers]").lastOrNull()?.text()?.toIntOrNull()
        if (lastChapterNumber != null && lastChapterNumber > 1) {
            for (i in 2..lastChapterNumber) {
                chapters.add(importChapter(i.toString(), mangaUrl, date, "Ch. $i"))
            }
        }
        
        return chapters
    }

    private fun parseDate(date: String): Long {
        return try {
            SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(date)?.time ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun importChapter(pageNumber: String, mangaUrl: String, date: Long, chapterName: String): MangaChapter {
        return MangaChapter(
            id = generateUid("$mangaUrl/$pageNumber"),
            title = chapterName,
            number = pageNumber.toFloatOrNull() ?: 0f,
            url = "$mangaUrl/$pageNumber",
            uploadDate = date,
            source = source,
            scanlator = null,
            branch = null,
            volume = 0,
        )
    }
}
