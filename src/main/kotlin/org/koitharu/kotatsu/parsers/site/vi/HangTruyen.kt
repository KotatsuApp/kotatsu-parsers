package org.koitharu.kotatsu.parsers.site.vi

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("HANGTRUYEN", "Hang Truyện", "vi")
internal class HangTruyen(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.HANGTRUYEN, 10) {

	override val configKeyDomain = ConfigKey.Domain("hangtruyen.org")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.UPDATED,
		SortOrder.UPDATED_ASC,
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_ASC,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
            isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.COMICS,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("/tim-kiem?page=")
			append(page)
			
			if (filter.types.isNotEmpty()) {
				append("&categoryIds=")
				val categoryIds = filter.types.joinToString(",") { type ->
					when (type) {
						ContentType.MANGA -> "1"
						ContentType.MANHUA -> "2"
						ContentType.MANHWA -> "3" 
						ContentType.COMICS -> "4,5"
						else -> "1,2,3,4,5"
					}
				}
				append(categoryIds)
			}
			
			if (filter.tags.isNotEmpty()) {
				append("&genreIds=")
				filter.tags.joinTo(this, separator = ",") { it.key }
			}

			append("&orderBy=")
			append(when (order) {
				SortOrder.POPULARITY -> "view_desc"
				SortOrder.POPULARITY_ASC -> "view_asc"
				SortOrder.UPDATED -> "udpated_at_date_desc"
				SortOrder.UPDATED_ASC -> "udpated_at_date_asc"
				SortOrder.NEWEST -> "created_at_date_desc"
				SortOrder.NEWEST_ASC -> "created_at_date_asc"
				else -> "view_desc"
			})

			if (!filter.query.isNullOrEmpty()) {
				append("&keyword=")
                val encodedQuery = filter.query.splitByWhitespace().joinToString(separator = "+") { part ->
					part.urlEncoded()
				}
				append(encodedQuery)
			}
		}

		val doc = webClient.httpGet(url.toAbsoluteUrl(domain)).parseHtml()
        return doc.select("div.m-post.col-md-6").map { div ->
            val href = div.selectFirst("h3.m-name a")?.attrAsRelativeUrl("href") ?: ""
            val ratingText = div.selectFirst("span")?.text()?.toFloatOrNull() ?: 0f
            val rating = (ratingText / 5f) * 5f
            val img = div.selectFirst("img.lzl")?.let { img ->
                img.attr("data-src").takeUnless { it.isNullOrEmpty() } ?: img.attr("data-original")
            }
            val title = div.selectFirst("h3.m-name a")?.text().orEmpty()
            Manga(
                id = generateUid(href),
                title = title,
                altTitles = emptySet(),
                url = href,
                publicUrl = href.toAbsoluteUrl(domain),
                rating = rating,
                contentRating = if (isNsfwSource) ContentRating.ADULT else ContentRating.SAFE,
                coverUrl = img,
                tags = emptySet(),
                state = null,
                authors = emptySet(),
                source = source,
            )
        }
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
		
		val script = doc.selectFirst("script:containsData(const mangaDetail)")?.data() ?: return manga
		val mangaDetailJson = script.substringAfter("const mangaDetail = ").substringBefore(";")
		val mangaDetail = JSONObject(mangaDetailJson)

		val mangaSlug = mangaDetail.getString("slug")
		val adultTagIds = setOf("29", "31", "210", "211", "175", "41", "212")
		
		val tags = mangaDetail.getJSONArray("genres").mapJSONToSet { genre ->
			MangaTag(
				key = genre.getInt("id").toString(),
				title = genre.getString("name").toTitleCase(sourceLocale),
				source = source,
			)
		}
		
		val isAdult = tags.any { it.key in adultTagIds }

		return manga.copy(
			title = mangaDetail.getString("title").orEmpty(),
			authors = mangaDetail.optString("author").takeUnless { it.isNullOrEmpty() }?.let { setOf(it) } ?: emptySet(),
			tags = tags,
			description = mangaDetail.getString("overview").orEmpty(),
			state = when (mangaDetail.optInt("status")) {
				0 -> MangaState.ONGOING
				1 -> MangaState.FINISHED  
				else -> null
			},
			contentRating = if (isAdult) ContentRating.ADULT else ContentRating.SAFE,
			chapters = mangaDetail.getJSONArray("chapters").mapJSON { chapter ->
				val chapterSlug = chapter.getString("slug")
				val chapterUrl = "$mangaSlug/$chapterSlug"
				MangaChapter(
					id = generateUid(chapterUrl),
					title = chapter.getString("name"),
					number = chapter.getDouble("index").toFloat(),
					url = chapterUrl,
					scanlator = null,
					uploadDate = dateFormat.parseSafe(chapter.getString("releasedAt")),
					branch = null,
					source = source,
					volume = 0
				)
			}.reversed()
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		
		val script = doc.selectFirst("script:containsData(const mangaDetail)")?.data() ?: return emptyList()
		val chapterDetailJson = script.substringAfter("const chapterDetail = ").substringBefore("}</script>") + "}"
		val chapterDetail = JSONObject(chapterDetailJson)
		
		return chapterDetail.getJSONArray("images").mapJSON { image ->
			val url = image.getString("path")
			val index = image.getInt("index")
			index to url
		}.sortedBy { it.first }.map { (_, url) ->
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
        val doc = webClient.httpGet("https://$domain/tim-kiem").parseHtml()
        return doc.select("div.list-genres span").mapToSet {
            MangaTag(
                key = it.attr("data-value"),
                title = it.text().replace("#", "").toTitleCase(sourceLocale),
                source = source,
            )
        }
    }
}
