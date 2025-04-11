package org.koitharu.kotatsu.parsers.site.en

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.getOrNull
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.Broken

@Broken("Need fix tags in getDetails")
@MangaSourceParser("BATCAVE", "BatCave", "en")
internal class BatCave(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.BATCAVE, 20) {

    override val configKeyDomain = ConfigKey.Domain("batcave.biz")

	private val availableTags = suspendLazy(initializer = ::fetchTags)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

    override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
            isMultipleTagsSupported = true,
            isSearchWithFiltersSupported = false,
            isYearRangeSupported = true
		)

    override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = availableTags.get()
	)

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val urlBuilder = StringBuilder()
        when {
            !filter.query.isNullOrEmpty() -> {
                urlBuilder.append("/search/")
                urlBuilder.append(filter.query.urlEncoded())
                if (page > 1) urlBuilder.append("/page/$page/")
            }
            else -> {
                urlBuilder.append("/ComicList")
                if (filter.yearFrom != YEAR_UNKNOWN) {
                    urlBuilder.append("/y[from]=${filter.yearFrom}")
                }
                if (filter.yearTo != YEAR_UNKNOWN) {
                    urlBuilder.append("/y[to]=${filter.yearTo}")
                }
                if (filter.tags.isNotEmpty()) {
                    urlBuilder.append("/g=")
                    urlBuilder.append(filter.tags.joinToString(",") { it.key })
                }
                urlBuilder.append("/sort")
                if (page > 1) { urlBuilder.append("/page/$page/") }
            }
        }

        val fullUrl = urlBuilder.toString().toAbsoluteUrl(domain)
        val doc = webClient.httpGet(fullUrl).parseHtml()
        return doc.select("div.readed.d-flex.short").map { item ->
            val a = item.selectFirst("a.readed__img.img-fit-cover.anim")
				?: throw ParseException("Link element not found!", fullUrl)
            val img = item.selectFirst("img[data-src]")
            val titleElement = item.selectFirst("h2.readed__title a")
            Manga(
                id = generateUid(a.attr("href")),
                url = a.attr("href"),
                publicUrl = a.attr("href"),
                title = titleElement.text(),
                altTitles = emptySet(),
                authors = emptySet(), 
                description = null,
                tags = emptySet(),
                rating = RATING_UNKNOWN,
                state = null,
                coverUrl = img.attr("data-src")?.toAbsoluteUrl(domain),
                contentRating = if (isNsfwSource) ContentRating.ADULT else null,
                source = source,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
        
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
        
        val scriptData = doc.selectFirst("script:containsData(__DATA__)")?.data()
            ?.substringAfter("window.__DATA__ = ")
            ?.substringBefore(";")
            ?: throw ParseException("Script data not found", manga.url)
            
        val jsonData = JSONObject(scriptData)
        val newsId = jsonData.getInt("news_id")
        val chaptersJson = jsonData.getJSONArray("chapters")
        
        val chapters = (0 until chaptersJson.length()).map { i ->
            val chapter = chaptersJson.getJSONObject(i)
            val chapterId = chapter.getInt("id")
            
            MangaChapter(
                id = generateUid("/reader/$newsId/$chapterId"),
                url = "/reader/$newsId/$chapterId",
                number = chapter.getInt("posi").toFloat(),
                title = chapter.getString("title"),
                uploadDate = runCatching { 
                    dateFormat.parse(chapter.getString("date"))?.time 
                }.getOrNull() ?: 0L,
                source = source,
                scanlator = null,
                branch = null,
                volume = 0,
            )
        }

		val author = doc.selectFirst("li:contains(Publisher:)")?.text()?.substringAfter("Publisher:")?.trim()
		val state = when (doc.selectFirst("li:contains(Release type:)")?.text()?.substringAfter("Release type:")?.trim()) {
			"Ongoing" -> MangaState.ONGOING
			else -> MangaState.FINISHED
		}

        val allTags = availableTags.get()
        val tags = doc.select("div.page__tags.d-flex a").mapNotNullToSet { a ->
            val tagName = a.text()
            allTags.find { it.title.equals(tagName, ignoreCase = true) }
        }

        return manga.copy(
            authors = setOfNotNull(author),
            state = state,
            chapters = chapters,
            description = doc.select("div.page__text.full-text.clearfix").text(),
            tags = tags
        )
    }

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val data = doc.selectFirst("script:containsData(__DATA__)")?.data()
			?.substringAfter("=")
			?.trim()
			?.removeSuffix(";")
			?.substringAfter("\"images\":[")
			?.substringBefore("]")
			?.split(",")
			?.map { it.trim().removeSurrounding("\"").replace("\\", "") }
			?: throw ParseException("Image data not found", chapter.url)

		return data.map { imageUrl ->
			MangaPage(
				id = generateUid(imageUrl),
				url = imageUrl,
				preview = null,
				source = source
			)
		}
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/comix/").parseHtml()
		val scriptData = doc.selectFirst("script:containsData(__XFILTER__)")?.data()
			?: throw ParseException("Script data not found", "$domain/genres")
			
		val genresJson = scriptData
			.substringAfter("\"g\":{")
			.substringBefore("}}}") + "}"
			
		val genresObj = JSONObject("{$genresJson}")
		val valuesArray = genresObj.getJSONArray("values")
		
		return (0 until valuesArray.length()).map { i ->
			val genre = valuesArray.getJSONObject(i)
			MangaTag(
				key = genre.getInt("id").toString(),
				title = genre.getString("value"),
				source = source
			)
		}.toSet()
	}
}