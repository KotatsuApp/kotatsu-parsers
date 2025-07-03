package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.arraySetOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyMangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import java.util.EnumSet

internal abstract class WebtoonsParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
) : LegacyMangaParser(context, source) {

	override val configKeyDomain = ConfigKey.Domain("webtoons.com")

	private val mobileApiDomain = "m.webtoons.com"
	private val staticDomain = "webtoon-phinf.pstatic.net"

	override val availableSortOrders: EnumSet<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.RATING,
		SortOrder.UPDATED,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override val userAgentKey = ConfigKey.UserAgent("Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = availableTags()
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		return page.url.toAbsoluteUrl(staticDomain)
	}

	private val languageCode: String
		get() = when (val tag = sourceLocale.toLanguageTag()) {
			"in" -> "id"
			"zh" -> "zh-hant"
			else -> tag
		}

	private suspend fun fetchEpisodes(titleNo: Long) : List<MangaChapter> {
		val url = "https://$mobileApiDomain/api/v1/webtoon/$titleNo/episodes?pageSize=99999"
		val json = webClient.httpGet(url).parseJson()

		val episodeList = json.optJSONObject("result")?.optJSONArray("episodeList")
			?: throw ParseException("No episodes found for title $titleNo", url)

		return episodeList.mapChapters { _, jo ->
			val episodeTitle = jo.getStringOrNull("episodeTitle") ?: ""
			val episodeNo = jo.getInt("episodeNo")
			val viewerLink = jo.getString("viewerLink")

			MangaChapter(
				id = generateUid("$titleNo-$episodeNo"),
				title = episodeTitle,
				number = episodeNo.toFloat(),
				volume = 0,
				url = viewerLink,
				uploadDate = jo.getLong("exposureDateMillis"),
				branch = null,
				scanlator = null,
				source = source,
			)
		}.sortedBy(MangaChapter::number)

	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val titleNo = manga.url.toLong()
		val detailsUrl = manga.publicUrl.ifBlank {
			"https://$domain/$languageCode/drama/placeholder/list?title_no=$titleNo"
		}

		val doc = webClient.httpGet(detailsUrl).parseHtml()

		val title = doc.select("meta[property='og:title']").attr("content")
			.ifEmpty { doc.select("h1.subj, h3.subj").text().ifEmpty { manga.title } }

		val description = listOf(
			doc.select("meta[property='og:description']").attr("content"),
			doc.select("#_asideDetail p.summary").text(),
			doc.select(".detail_header .summary").text()
		).firstOrNull { it.isNotBlank() }.orEmpty()

		val coverUrl = doc.select("meta[property=\"og:image\"]").attr("content").let { url ->
			if (url.isNotBlank()) url.toAbsoluteUrl(staticDomain) else manga.coverUrl
		}

		val author = listOf(
			doc.select("meta[property='com-linewebtoon:webtoon:author']").attr("content"),
			doc.select(".detail_header .info .author").firstOrNull()?.text(),
			doc.select(".author_area").text()
		).firstOrNull { !it.isNullOrBlank() && it != "null" }

		val genreElements = doc.select(".detail_header .info .genre").ifEmpty {
			doc.select("h2.genre")
		}
		val genres = genreElements.map { it.text() }.toSet()

		val dayInfo = doc.select("#_asideDetail p.day_info").text().ifEmpty {
			doc.select(".day_info").text()
		}
		val state = when {
			dayInfo.contains("UP") || dayInfo.contains("EVERY") || dayInfo.contains("NOUVEAU") -> MangaState.ONGOING
			dayInfo.contains("END") || dayInfo.contains("COMPLETED") || dayInfo.contains("TERMINÉ") -> MangaState.FINISHED
			else -> null
		}

		val chapters = async { fetchEpisodes(titleNo) }.await()

		Manga(
			id = generateUid(titleNo),
			title = title,
			altTitles = emptySet(),
			url = "$titleNo",
			publicUrl = detailsUrl,
			rating = RATING_UNKNOWN,
			contentRating = null,
			coverUrl = coverUrl,
			largeCoverUrl = null,
			tags = genres.map { genre -> MangaTag(title = genre, key = genre.lowercase(), source = source) }.toSet(),
			authors = setOfNotNull(author.takeIf { it != "null" }),
			description = description,
			state = state,
			chapters = chapters,
			source = source,
		)
	}

	private fun getSortOrderParam(order: SortOrder): String {
		return when (order) {
			SortOrder.POPULARITY -> "MANA"
			SortOrder.RATING -> "LIKEIT"
			SortOrder.UPDATED -> "UPDATE"
			else -> "MANA"
		}
	}

	private fun availableTags() = arraySetOf(
		MangaTag("Action", "action", source),
		MangaTag("Comedy", "comedy", source),
		MangaTag("Drama", "drama", source),
		MangaTag("Fantasy", "fantasy", source),
		MangaTag("Horror", "horror", source),
		MangaTag("Romance", "romance", source),
		MangaTag("Sci-Fi", "sf", source),
		MangaTag("Slice of Life", "slice_of_life", source),
		MangaTag("Sports", "sports", source),
		MangaTag("Supernatural", "supernatural", source),
		MangaTag("Thriller", "thriller", source),
		MangaTag("Historical", "historical", source),
		MangaTag("Mystery", "mystery", source),
		MangaTag("Superhero", "super_hero", source),
		MangaTag("Heartwarming", "heartwarming", source),
		MangaTag("Graphic Novel", "graphic_novel", source),
		MangaTag("Informative", "tiptoon", source),
	)

	private val genreUrlMap: Map<String, String> = availableTags().associate {
		it.title.lowercase() to it.key
	}

	override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val document = when {
			!filter.query.isNullOrEmpty() -> {
				val searchUrl = "https://$domain/$languageCode/search?keyword=${filter.query.urlEncoded()}"
				webClient.httpGet(searchUrl).parseHtml()
			}
			filter.tags.isNotEmpty() -> {
				val selectedGenre = filter.tags.first()
				val genreUrlPath = genreUrlMap[selectedGenre.key] ?: selectedGenre.key
				val sortParam = getSortOrderParam(order)
				val genreUrl = "https://$domain/$languageCode/genres/$genreUrlPath?sortOrder=$sortParam"
				webClient.httpGet(genreUrl).parseHtml()
			}
			else -> {
				val rankingType = when (order) {
					SortOrder.POPULARITY -> "popular"
					SortOrder.RATING -> "trending"
					SortOrder.UPDATED -> "originals"
					else -> "popular"
				}
				val rankingUrl = "https://$domain/$languageCode/ranking/$rankingType"
				webClient.httpGet(rankingUrl).parseHtml()
			}
		}

		val selectedGenreForManga = if (filter.tags.isNotEmpty()) filter.tags.first() else null

		return document.select(".webtoon_list li a, .card_wrap .card_item a")
			.map { element -> createMangaFromElement(element, source, selectedGenreForManga) }
			.drop(offset)
			.take(20)
	}

	private fun createMangaFromElement(element: Element, source: MangaParserSource, selectedGenre: MangaTag? = null): Manga {
		val href = element.absUrl("href")
		val titleNo = extractTitleNoFromUrl(href)
		val title = element.select(".title, .card_title").text()
		val thumbnailUrl = element.select("img").attr("src")

		return Manga(
			id = generateUid(titleNo),
			title = title,
			altTitles = emptySet(),
			url = titleNo.toString(),
			publicUrl = href,
			rating = RATING_UNKNOWN,
			contentRating = null,
			coverUrl = thumbnailUrl.toAbsoluteUrl(staticDomain),
			largeCoverUrl = null,
			tags = selectedGenre?.let { setOf(it) } ?: emptySet(),
			authors = emptySet(),
			description = null,
			state = null,
			source = source,
		)
	}

	private fun extractTitleNoFromUrl(url: String): Long {
		return Regex("title_no=(\\d+)").find(url)?.groupValues?.get(1)?.toLong()
			?: throw ParseException("Could not extract title_no from URL: $url", url)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = try {
			val absUrl = chapter.url.toAbsoluteUrl(domain)
			webClient.httpGet(absUrl).parseHtml()
		} catch (e: Exception) {
			throw ParseException("Failed to get pages for chapter: ${chapter.title}", chapter.url, e)
		}

		fun extractImages(selector: String, attr: String = "data-url"): List<MangaPage> {
			return doc.select(selector).mapIndexedNotNull { i, element ->
				val url = element.attr(attr).takeIf { it.isNotBlank() }
					?: element.attr("src").takeIf { it.contains(staticDomain) }
					?: return@mapIndexedNotNull null
				MangaPage(
					id = generateUid("${chapter.id}-$i"),
					url = url,
					preview = null,
					source = source
				)
			}
		}

		return extractImages("div#_imageList > img")
			.ifEmpty { extractImages("canvas[data-url]") }
			.ifEmpty { extractImages("img[src*='$staticDomain'], img[data-url*='$staticDomain']") }
			.ifEmpty { throw ParseException("No images found in chapter.", chapter.url) }
	}

	@MangaSourceParser("WEBTOONS_EN", "Webtoons English", "en", type = ContentType.MANGA)
	class English(context: MangaLoaderContext) : WebtoonsParser(context, MangaParserSource.WEBTOONS_EN)

	@MangaSourceParser("WEBTOONS_ID", "Webtoons Indonesia", "id", type = ContentType.MANGA)
	class Indonesian(context: MangaLoaderContext) : WebtoonsParser(context, MangaParserSource.WEBTOONS_ID)

	@MangaSourceParser("WEBTOONS_ES", "Webtoons Spanish", "es", type = ContentType.MANGA)
	class Spanish(context: MangaLoaderContext) : WebtoonsParser(context, MangaParserSource.WEBTOONS_ES)

	@MangaSourceParser("WEBTOONS_FR", "Webtoons French", "fr", type = ContentType.MANGA)
	class French(context: MangaLoaderContext) : WebtoonsParser(context, MangaParserSource.WEBTOONS_FR)

	@MangaSourceParser("WEBTOONS_TH", "Webtoons Thai", "th", type = ContentType.MANGA)
	class Thai(context: MangaLoaderContext) : WebtoonsParser(context, MangaParserSource.WEBTOONS_TH)

	@MangaSourceParser("WEBTOONS_ZH", "Webtoons Chinese", "zh", type = ContentType.MANGA)
	class Chinese(context: MangaLoaderContext) : WebtoonsParser(context, MangaParserSource.WEBTOONS_ZH)

	@MangaSourceParser("WEBTOONS_DE", "Webtoons German", "de", type = ContentType.MANGA)
	class German(context: MangaLoaderContext) : WebtoonsParser(context, MangaParserSource.WEBTOONS_DE)
}
