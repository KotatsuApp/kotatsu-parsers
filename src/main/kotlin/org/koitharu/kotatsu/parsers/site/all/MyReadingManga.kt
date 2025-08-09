package org.koitharu.kotatsu.parsers.site.all

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale
import java.util.regex.Pattern

@MangaSourceParser("MYREADINGMANGA", "MyReadingManga", "en", ContentType.HENTAI)
internal class MyReadingManga(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.MYREADINGMANGA, 18) {

	override val configKeyDomain = ConfigKey.Domain("myreadingmanga.info")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
	)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchTags(),
		availableStates = EnumSet.of(
			MangaState.ONGOING,
			MangaState.FINISHED,
		),
		availableContentRating = EnumSet.of(ContentRating.ADULT),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)

			when {
				!filter.query.isNullOrEmpty() -> {
					append("/page/")
					append(page)
					append("/?s=")
					append(filter.query.urlEncoded())
				}

				filter.tags.isNotEmpty() -> {
					append("/genre/")
					append(filter.tags.first().key)
					append("/page/")
					append(page)
					append("/")
				}

				filter.states.isNotEmpty() -> {
					append("/status/")
					append(
						when (filter.states.first()) {
							MangaState.ONGOING -> "ongoing"
							MangaState.FINISHED -> "completed"
							else -> "ongoing"
						},
					)
					append("/page/")
					append(page)
					append("/")
				}

				else -> {
					append("/page/")
					append(page)
					append("/")
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return parseMangaList(doc)
	}

	private fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("div.content-archive article.post:not(.category-video)").mapNotNull { element ->
			val titleElement = element.selectFirst("h2.entry-title a") ?: return@mapNotNull null
			val thumbnailElement = element.selectFirst("a.entry-image-link img")

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
		val title = doc.selectFirst("h1.entry-title")?.text() ?: manga.title

		val altTitles = mutableSetOf<String>()
		val altTitleElement = doc.selectFirst("p.alt-title-class")
		if (altTitleElement != null) {
			var nextElement = altTitleElement.nextElementSibling()
			while (nextElement != null && nextElement.tagName() == "p" &&
				!nextElement.hasClass("info-class") && !nextElement.hasClass("chapter-class")
			) {
				val altTitle = nextElement.text().trim()
				if (altTitle.isNotEmpty()) {
					altTitles.add(altTitle)
				}
				nextElement = nextElement.nextElementSibling()
			}
		}

		var description = ""
		val descriptionElement = doc.selectFirst("p.info-class")
		if (descriptionElement != null) {
			var nextElement = descriptionElement.nextElementSibling()
			val descParts = mutableListOf<String>()
			while (nextElement != null && nextElement.tagName() == "p" &&
				!nextElement.hasClass("chapter-class") && !nextElement.hasClass("alt-title-class")
			) {
				val text = nextElement.text().trim()
				if (text.isNotEmpty()) {
					descParts.add(text)
				}
				nextElement = nextElement.nextElementSibling()
			}
			description = descParts.joinToString("\n\n")
		}

		if (description.isEmpty()) {
			description = doc.select("div.entry-content p strong")
				.joinToString("\n") { it.text() }
				.trim()
				.ifEmpty { title }
		}

		val authorFromTitle = title.substringAfter("[").substringBefore("]").trim()
		val authorFromTag = doc.select("span.entry-tags a[href*='/tag/']")
			.firstOrNull { it.text().contains("(") && it.text().contains(")") }
			?.text()?.trim()
		val author = authorFromTag ?: authorFromTitle

		val genres = mutableSetOf<MangaTag>()

		doc.select("span.entry-terms:has(span:contains(Genres)) a").forEach {
			genres.add(
				MangaTag(
					title = it.text().trim(),
					key = it.attr("href").substringAfterLast("/genre/").substringBefore("/"),
					source = source,
				),
			)
		}

		val state = when (doc.select("a[href*=status]").firstOrNull()?.text()) {
			"Ongoing" -> MangaState.ONGOING
			"Completed" -> MangaState.FINISHED
			else -> null
		}

		val chapters = parseChapters(doc)

		return manga.copy(
			altTitles = altTitles,
			description = description,
			tags = genres,
			state = state,
			authors = setOfNotNull(author.takeIf { it.isNotEmpty() && it != title }),
			chapters = chapters,
		)
	}


	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()

		val images = doc.select("div.entry-content img.img-myreadingmanga, div.entry-content div > img")
			.filter { element ->
				val src = findImageSrc(element)
				src != null && !src.contains("GH-") && !src.contains("nucarnival") &&
					!src.contains("/wp-content/uploads/202") // Exclude old uploads that might be ads
			}
			.mapNotNull { findImageSrc(it) }
			.distinct()

		return images.mapIndexed { index, url ->
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml()
		return doc.select("h4.widget-title.widgettitle:contains(Genres) + .tagcloud a")
			.mapToSet { element ->

				MangaTag(
					title = element.text().substringBefore(" ("),
					key = element.attr("href").trimEnd('/').substringAfterLast('/'),
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
		val mangaUrl = document.baseUri().removeSuffix("/")
		val date = parseDate(document.select("time.entry-time").text())

		// Look for chapter information
		val chapterClass = document.selectFirst("div.chapter-class")

		// Check if there's a chapter title after the chapter-class div
		var chapterTitle: String? = null
		if (chapterClass != null) {
			var nextElement = chapterClass.nextElementSibling()
			while (nextElement != null && nextElement.tagName() != "div") {
				if (nextElement.tagName() == "p" && nextElement.text().contains("Chapter", ignoreCase = true)) {
					chapterTitle = nextElement.text().trim()
					break
				}
				nextElement = nextElement.nextElementSibling()
			}
		}

		// Check for pagination
		val paginationInContent =
			document.select("div.entry-pagination a.page-numbers, div.chapter-class .entry-pagination a.page-numbers")
				.mapNotNull { it.text().toIntOrNull() }
				.maxOrNull()

		if (paginationInContent != null && paginationInContent > 1) {
			// Multi-page manga with chapters
			for (i in 1..paginationInContent) {
				val title = when {
					chapterTitle != null && i == 1 -> chapterTitle
					chapterTitle != null -> chapterTitle.replace("1", i.toString())
					else -> "Chapter $i"
				}

				chapters.add(
					MangaChapter(
						id = generateUid("$mangaUrl/$i"),
						title = title,
						number = i.toFloat(),
						url = if (i == 1) mangaUrl else "$mangaUrl/$i/",
						uploadDate = date,
						source = source,
						scanlator = null,
						branch = null,
						volume = 0,
					),
				)
			}
		} else {
			// Single page manga or no pagination found
			chapters.add(
				MangaChapter(
					id = generateUid(mangaUrl),
					title = chapterTitle ?: "Complete",
					number = 1f,
					url = mangaUrl,
					uploadDate = date,
					source = source,
					scanlator = null,
					branch = null,
					volume = 0,
				),
			)
		}

		return chapters
	}

	private fun parseDate(date: String): Long {
		return try {
			SimpleDateFormat("MMMM dd, yyyy", Locale.US).parse(date)?.time ?: 0
		} catch (_: Exception) {
			0L
		}
	}
}

