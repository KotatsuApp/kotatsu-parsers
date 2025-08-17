package org.koitharu.kotatsu.parsers.site.fr

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseRaw
import org.koitharu.kotatsu.parsers.util.toRelativeUrl
import org.koitharu.kotatsu.parsers.util.urlDecode
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.util.EnumSet

@MangaSourceParser("ANIMESAMA", "AnimeSama", "fr")
internal class AnimeSama(context: MangaLoaderContext) :
	PagedMangaParser(context, source = MangaParserSource.ANIMESAMA, 96) {

	override val configKeyDomain = ConfigKey.Domain("anime-sama.fr")
	private val baseUrl = "https://$domain"
	private val cdnUrl = "$baseUrl/s2/scans/"

	override fun getRequestHeaders() = Headers.Builder()
		.add("Referer", baseUrl)
		.build()

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.ALPHABETICAL,
	)

	override val filterCapabilities = MangaListFilterCapabilities(
		isSearchSupported = true,
		isSearchWithFiltersSupported = true,
		isMultipleTagsSupported = true,
	)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		val doc = webClient.httpGet("$baseUrl/catalogue").parseHtml()
		val genres = doc.select("#list_genres label").mapNotNull { labelElement ->
			val input = labelElement.selectFirst("input[name=genre[]]") ?: return@mapNotNull null
			val labelText = labelElement.ownText()
			val value = input.attr("value")
			MangaTag(
				key = value,
				title = labelText,
				source = source,
			)
		}.toSet()

		return MangaListFilterOptions(
			availableTags = genres,
		)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildListUrl(page, order, filter)
		val doc = webClient.httpGet(url).parseHtml()

		return if (url.toString() == "$baseUrl/") {
			parseHomePageScans(doc)
		} else {
			parseCataloguePage(doc)
		}
	}

	private fun buildListUrl(page: Int, order: SortOrder, filter: MangaListFilter) = when {
		filter.query.isNullOrEmpty().not() || filter.tags.isNotEmpty() -> {
			"$baseUrl/catalogue".toHttpUrl().newBuilder()
				.addQueryParameter("type[0]", "Scans")
				.apply {
					filter.query?.let { addQueryParameter("search", it) }
					filter.tags.forEach { tag ->
						addQueryParameter("genre[]", tag.key)
					}
				}
				.addQueryParameter("page", page.toString())
				.build()
		}

		order == SortOrder.UPDATED && page == 1 -> baseUrl.toHttpUrl()
		else -> "$baseUrl/catalogue".toHttpUrl().newBuilder()
			.addQueryParameter("type[0]", "Scans")
			.addQueryParameter("page", page.toString())
			.build()
	}

	private fun parseCataloguePage(doc: Document): List<Manga> {
		return doc.select("#list_catalog > div").mapNotNull { element ->
			val a = element.selectFirst("a") ?: return@mapNotNull null
			val title = element.selectFirst("h1")?.text() ?: return@mapNotNull null
			val cover = element.selectFirst("img")?.attr("src") ?: return@mapNotNull null
			val href = a.attr("href").removeSuffix("/")

			createManga(
				href = href,
				title = title,
				cover = cover
			)
		}
	}

	private fun parseHomePageScans(doc: Document): List<Manga> {
		val mangaList = mutableListOf<Manga>()
		val seenUrls = mutableSetOf<String>()

		doc.select("#containerAjoutsScans > div").forEach { element ->
			parseHomePageManga(element)?.let { manga ->
				if (seenUrls.add(manga.url)) {
					mangaList.add(manga)
				}
			}
		}

		val selectors = listOf("#containerSorties", "#containerClassiques", "#containerPepites")
		doc.select(selectors.joinToString(", ") { "$it > div" }).forEach { element ->
			parseHorizontalManga(element)?.let { manga ->
				if (manga.url.contains("/scan") && seenUrls.add(manga.url)) {
					mangaList.add(manga)
				}
			}
		}

		return mangaList
	}

	private fun parseHorizontalManga(element: Element): Manga? {
		val a = element.selectFirst("a") ?: return null
		val title = element.selectFirst("h1")?.text() ?: return null
		val cover = element.selectFirst("img")?.attr("src") ?: return null

		val href = a.attr("href")
		val types = element.select("p").getOrNull(2)?.text().orEmpty()
		if (!types.contains("Scans")) return null

		return createManga(href, title, cover)
	}

	private fun parseHomePageManga(element: Element): Manga? {
		val a = element.selectFirst("a") ?: return null
		val title = element.selectFirst("h1")?.text() ?: return null
		val cover = element.selectFirst("img")?.attr("src") ?: return null
		val href = a.attr("href").removeSuffix("/scan/vf/")

		return createManga(href, title, cover)
	}

	private fun createManga(href: String, title: String, cover: String) = Manga(
		id = generateUid(href),
		title = normalizeTitle(title),
		altTitles = emptySet(),
		url = href.toRelativeUrl(domain),
		publicUrl = href,
		rating = RATING_UNKNOWN,
		contentRating = ContentRating.SAFE,
		coverUrl = cover,
		largeCoverUrl = null,
		tags = emptySet(),
		state = null,
		authors = emptySet(),
		description = null,
		chapters = null,
		source = source,
	)

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.publicUrl.toHttpUrl()).parseHtml()

		val description = doc.selectFirst("#sousBlocMiddle > div h2:contains(Synopsis)+p")?.text()
		val genreText = doc.select("#sousBlocMiddle > div h2:contains(Genres)+a").text()
		val title = doc.selectFirst("#titreOeuvre")?.text() ?: manga.title
		val cover = doc.selectFirst("#coverOeuvre")?.attr("src")

		val genres = genreText.split("-", ",")
			.mapNotNull { genre ->
				genre.trim().takeIf { it.isNotEmpty() }?.let {
					MangaTag(key = it, title = it, source = source)
				}
			}
			.toSet()

		val chapters = parseChapters(manga.url, title)
		return manga.copy(
			title = normalizeTitle(title),
			description = description,
			tags = genres,
			chapters = chapters,
			coverUrl = cover ?: manga.coverUrl,
		)
	}

	private suspend fun parseChapters(mangaUrl: String, mangaTitle: String): List<MangaChapter> {
		return try {
			val subDoc = webClient.httpGet("$baseUrl$mangaUrl/scan/vf").parseHtml()
			parseChapterListFromJs(subDoc).ifEmpty {
				parseChaptersFromJsonApi(mangaUrl, mangaTitle)
			}
		} catch (_: Exception) {
			parseChaptersFromJsonApi(mangaUrl, mangaTitle)
		}
	}

	private suspend fun parseChapterListFromJs(doc: Document): List<MangaChapter> {
		val title = doc.selectFirst("#titreOeuvre")?.text().orEmpty()
		val chapterUrlBuilder = doc.baseUri().toHttpUrl()
			.newBuilder()
			.query(null)
			.addPathSegment("episodes.js")
			.addQueryParameter("title", title)

		val jsContent = webClient.httpGet(chapterUrlBuilder.build()).parseRaw()
		val episodeNumbers = Regex("""eps(\d+)""").findAll(jsContent)
			.mapNotNull { it.groupValues[1].toIntOrNull() }
			.distinct()
			.sorted()
			.toList()

		return episodeNumbers.mapIndexed { index, number ->
			val chapterId = index + 1
			val url = chapterUrlBuilder
				.addQueryParameter("id", chapterId.toString())
				.build()
				.toString()
				.removePrefix("https://$domain")

			MangaChapter(
				id = generateUid(url),
				title = "Chapitre $number",
				number = number.toFloat(),
				volume = 0,
				url = url,
				scanlator = null,
				uploadDate = 0,
				branch = null,
				source = source,
			)
		}
	}

	private suspend fun parseChaptersFromJsonApi(
		mangaUrl: String,
		mangaTitle: String
	): List<MangaChapter> {
		val chapterInfoUrl =
			"$baseUrl/s2/scans/get_nb_chap_et_img.php?oeuvre=${mangaTitle.urlEncoded()}"

		return try {
			val chapterInfo = webClient.httpGet(chapterInfoUrl).parseJson()
			chapterInfo.keys().asSequence()
				.mapNotNull { it.toIntOrNull() }
				.sorted()
				.map { chapterNumber ->
					val dataUrl = "$mangaUrl#${mangaTitle.urlEncoded()}#$chapterNumber"
					MangaChapter(
						id = generateUid(dataUrl),
						title = "Chapitre $chapterNumber",
						number = chapterNumber.toFloat(),
						volume = 0,
						url = dataUrl,
						scanlator = null,
						uploadDate = 0,
						branch = null,
						source = source,
					)
				}.toList()
		} catch (_: Exception) {
			emptyList()
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val encodedTitle = extractEncodedTitle(chapter.url) ?: return emptyList()
		val chapterInfoUrl = "$baseUrl/s2/scans/get_nb_chap_et_img.php?oeuvre=$encodedTitle"
		val decodedTitle = encodedTitle.urlDecode()

		return try {
			val chapterInfo = webClient.httpGet(chapterInfoUrl).parseJson()
			val chapterKey = chapter.number.toInt().toString()
			val pageCount = chapterInfo.optInt(chapterKey)

			if (pageCount == 0) return emptyList()

			(1..pageCount).map { pageIndex ->
				MangaPage(
					id = generateUid("${chapter.id}_$pageIndex"),
					url = "$cdnUrl$decodedTitle/$chapterKey/$pageIndex.jpg",
					preview = null,
					source = source,
				)
			}
		} catch (_: Exception) {
			emptyList()
		}
	}

	private fun normalizeTitle(title: String) = title.replace("’", "'")

	private fun extractEncodedTitle(url: String): String? {
		return if ('#' in url) {
			url.split('#').getOrNull(1)
		} else {
			"$baseUrl$url".toHttpUrl().queryParameter("title")
		}
	}
}
