package org.koitharu.kotatsu.parsers.site.madara.es

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("DOUJIN_HENTAI_NET", "DoujinHentai.net", "es", ContentType.HENTAI)
internal class DoujinHentaiNet(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.DOUJIN_HENTAI_NET, "doujinhentai.net", 18) {

	override val datePattern = "dd MMM. yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
	override val listUrl = "/list-manga-hentai"
	override val tagPrefix = "/list-manga-hentai/category/"
	override val selectTestAsync = "div.listing-chapters_wrap"
	override val selectChapter = "li.wp-manga-chapter:contains(Capitulo)"
	override val selectPage = "div#all img"
	override val selectDesc = "div.description-summary div.summary__content"

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableContentRating = EnumSet.of(ContentRating.ADULT),
	)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val pageNum = page + 1
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/search?query=")
					append(filter.query.urlEncoded())
					if (pageNum > 1) {
						append("&page=")
						append(pageNum)
					}
				}

				filter.tags.isNotEmpty() -> {
					val tag = filter.tags.first().key
					append("/list-manga-hentai/category/")
					append(tag)
					append("?")
					if (pageNum > 1) {
						append("page=")
						append(pageNum)
						append("&")
					}
					append("orderby=")
					append(
						when (order) {
							SortOrder.ALPHABETICAL -> "alphabet"
							SortOrder.UPDATED -> "last"
							SortOrder.POPULARITY -> "views"
							else -> ""
						},
					)

				}

				else -> {
					append("/list-manga-hentai")
					append("?")
					if (pageNum > 1) {
						append("page=")
						append(pageNum)
						append("&")
					}

					append("orderby=")
					append(
						when (order) {
							SortOrder.ALPHABETICAL -> "alphabet"
							SortOrder.UPDATED -> "last"
							SortOrder.POPULARITY -> "views"
							else -> ""
						},
					)
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return parseMangaList(doc)
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val altTitle = doc.selectFirst("div.post-content_item:has(h5:matches(Titulo Alt)) .summary-content")
			?.text()?.takeIf { it.isNotBlank() && !it.equals("Desconocido", true) }
		val authors = mutableSetOf<String>().apply {
			doc.select("div.author-content").text().trim().takeIf { it.isNotBlank() }?.let { add(it) }
			doc.select("div.artist-content").text().trim().takeIf { it.isNotBlank() }?.let { add(it) }
		}
		val desc = doc.select(selectDesc).html()
		val href = doc.selectFirst("head meta[property='og:url']")?.attr("content")?.toRelativeUrl(domain) ?: manga.url
		val testCheckAsync = doc.select(selectTestAsync)
		val chaptersDeferred = if (testCheckAsync.isEmpty()) {
			async { loadChapters(href, doc) }
		} else {
			async { getChapters(manga, doc) }
		}

		manga.copy(
			title = doc.selectFirst("h3")?.text()?.replace("Doujin Hentai: ", "") ?: manga.title,
			altTitles = setOfNotNull(altTitle),
			authors = authors,
			url = href,
			publicUrl = href.toAbsoluteUrl(domain),
			tags = doc.body().select(selectGenre).mapToSet { a -> createMangaTag(a) }.filterNotNull().toSet(),
			description = desc,
			chapters = chaptersDeferred.await(),
		)
	}

	override fun parseMangaList(doc: Document): List<Manga> {
		val isNotSearch = doc.select("div.page-content-listing > div.col-sm-6.col-md-3.col-xs-12").isNotEmpty()

		val items = if (isNotSearch) {
			doc.select("div.page-content-listing > div.col-sm-6.col-md-3.col-xs-12")
		} else {
			doc.select("div.c-tabs-item__content > div.c-tabs-item__content")
		}

		return items.mapNotNull { div ->

			val a = if (isNotSearch) {
				div.selectFirst("a.thumbnail")
			} else {
				div.selectFirst("div.tab-thumb a")
			} ?: return@mapNotNull null

			val href = a.attr("href")
			val img = a.selectFirst("img")
			val cover = img?.attr("data-src")?.takeIf { it.isNotBlank() } ?: img?.attr("src")

			val title = if (isNotSearch) {
				a.selectFirst("span.card-title")?.text()?.removePrefix("Leer ")?.trim()
					?: a.attr("title")
			} else {
				div.selectFirst("div.post-title a")?.text()?.trim() ?: a.attr("title")
			}
			Manga(
				id = generateUid(href),
				url = href.toRelativeUrl(domain),
				publicUrl = href,
				coverUrl = cover,
				title = title,
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

	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.select("li.wp-manga-chapter").mapIndexedNotNull { i, li ->
			val a = li.selectFirst("a") ?: return@mapIndexedNotNull null
			val href = a.attr("href")
			val name = a.text()
			val dateText = li.selectFirst("span.chapter-release-date i")?.text()

			MangaChapter(
				id = generateUid(href),
				title = name,
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = dateFormat.parseSafe(dateText),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPage).map { div ->
			val img = div.selectFirstOrThrow("img")
			val url = img.requireSrc().toRelativeUrl(domain)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		val genre = doc.body().selectFirst("div.genres_wrap div.genres")
			?: doc.parseFailed("Genre not found")

		val keySet = mutableSetOf<String>()

		return genre.children().mapNotNullToSet { a ->
			val href = a.attr("href")
				.replace("lista-manga-hentai", "list-manga-hentai")
				.removeSuffix("/")
				.substringAfterLast(tagPrefix, "")
				.takeIf { it.isNotEmpty() && keySet.add(it) } ?: return@mapNotNullToSet null

			MangaTag(
				key = href,
				title = a.ownText().ifEmpty { href }.toTitleCase(sourceLocale),
				source = source,
			)
		}
	}
}

