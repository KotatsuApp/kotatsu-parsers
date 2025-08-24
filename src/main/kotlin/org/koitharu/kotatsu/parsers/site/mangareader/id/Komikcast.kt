package org.koitharu.kotatsu.parsers.site.mangareader.id

import kotlinx.coroutines.delay
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.WordSet
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.ownTextOrNull
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseSafe
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.textOrNull
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("KOMIKCAST", "KomikCast", "id")
internal class Komikcast(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.KOMIKCAST, "komikcast.li", pageSize = 60, searchPageSize = 28) {

	override val userAgentKey = ConfigKey.UserAgent(
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
	)

	override fun getRequestHeaders() = super.getRequestHeaders().newBuilder()
		.add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
		.add("Accept-Language", "en-US,en;q=0.5")
		.add("Cache-Control", "no-cache")
		.add("Pragma", "no-cache")
		.build()

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val newRequest = when {
			request.url.pathSegments.contains("chapter") -> {
				// Add referer for chapter pages
				val mangaSlug = request.url.toString()
					.substringAfter("/chapter/")
					.substringBefore("-chapter-")
					.substringBefore("-ch-")
				request.newBuilder()
					.header("Referer", "https://$domain/komik/$mangaSlug/")
					.build()
			}
			else -> {
				request.newBuilder()
					.header("Referer", "https://$domain/")
					.build()
			}
		}
		return chain.proceed(newRequest)
	}

	override val listUrl = "/daftar-komik"
	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.ALPHABETICAL, SortOrder.ALPHABETICAL_DESC)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false
		)

	override suspend fun getFilterOptions() = super.getFilterOptions().copy(
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)

			when {

				!filter.query.isNullOrEmpty() -> {
					append("/page/")
					append(page.toString())
					append("/?s=")
					append(filter.query.urlEncoded())
				}

				else -> {
					append(listUrl)
					append("/page/")
					append(page.toString())
					append("/?")

					filter.types.oneOrThrowIfMany()?.let { contentType ->
						append("type=")
						append(when (contentType) {
							ContentType.MANGA -> "manga"
							ContentType.MANHWA -> "manhwa"
							ContentType.MANHUA -> "manhua"
							else -> ""
						})
						append("&")
					}

					append(
						when (order) {
							SortOrder.ALPHABETICAL -> "orderby=titleasc"
							SortOrder.ALPHABETICAL_DESC -> "orderby=titledesc"
							SortOrder.POPULARITY -> "orderby=popular"
							else -> "sortby=update"
						},
					)

					val tagKey = "genre[]".urlEncoded()
					val tagQuery =
						if (filter.tags.isEmpty()) ""
						else filter.tags.joinToString(separator = "&", prefix = "&") { "$tagKey=${it.key}" }
					append(tagQuery)

					if (filter.states.isNotEmpty()) {
						filter.states.oneOrThrowIfMany()?.let {
							append("&status=")
							append(
								when (it) {
									MangaState.ONGOING -> "Ongoing"
									MangaState.FINISHED -> "Completed"
									else -> ""
								}
							)
						}
					}
				}
			}
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val mangaUrl = manga.url.toAbsoluteUrl(domain)

		repeat(3) { attempt ->
			if (attempt > 0) {
				delay(1500)
			}

			val docs = webClient.httpGet(mangaUrl).parseHtml()
			val chapterElements = docs.select("#chapter-wrapper > li")
			val dateFormat = SimpleDateFormat(datePattern, sourceLocale)

			val chapters = chapterElements.mapChapters(reversed = true) { index, element ->
				val url = element.selectFirst("a.chapter-link-item")?.attrAsRelativeUrl("href")
					?: return@mapChapters null
				MangaChapter(
					id = generateUid(url),
					title = element.selectFirst("a.chapter-link-item")?.ownTextOrNull(),
					url = url,
					number = index + 1f,
					volume = 0,
					scanlator = null,
					uploadDate = parseChapterDate(
						dateFormat,
						element.selectFirst("div.chapter-link-time")?.text(),
					),
					branch = null,
					source = source,
				)
			}

			if (chapters.isNotEmpty()) {
				return parseInfo(docs, manga, chapters)
			}
		}

		throw Exception("Failed to get manga details after 3 attempts for: $mangaUrl")
	}

	override suspend fun parseInfo(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
		val tagMap = getOrCreateTagMap()
		val tags = docs.select(".komik_info-content-genre > a").mapNotNullToSet { tagMap[it.text()] }
		val state = docs.selectFirst(".komik_info-content-meta span:contains(Status)")?.html()
		val mangaState = if (state?.contains("Ongoing") == true) {
			MangaState.ONGOING
		} else {
			MangaState.FINISHED
		}
		val author = docs.selectFirst(".komik_info-content-meta span:contains(Author)")
			?.lastElementChild()?.textOrNull()
		val nsfw = docs.select("div")
			.any { it.text().contains("Peringatan", ignoreCase = true) && it.text().contains("konten", ignoreCase = true) }

		val title = docs.selectFirst("h1.komik_info-content-body-title")?.text()!!
			.replace(" Bahasa Indonesia", "").trim()
		val description = docs.selectFirst("div.komik_info-description-sinopsis")?.text()

		return manga.copy(
			title = title,
			description = description,
			state = mangaState,
			authors = setOfNotNull(author),
			contentRating = if (isNsfwSource || nsfw || manga.contentRating == ContentRating.ADULT) ContentRating.ADULT else ContentRating.SAFE,
			tags = tags,
			chapters = chapters,
		)
	}

	override fun parseMangaList(docs: Document): List<Manga> {
		return docs.select("div.list-update_item").mapNotNull {
			val a = it.selectFirstOrThrow("a.data-tooltip")
			val relativeUrl = a.attrAsRelativeUrl("href")
			val rating = it.selectFirst(".numscore")?.text()?.toFloatOrNull()?.div(10) ?: RATING_UNKNOWN
			val name = it.selectFirst("h3.title")?.text().orEmpty()
			Manga(
				id = generateUid(relativeUrl),
				url = relativeUrl,
				title = name,
				altTitles = emptySet(),
				publicUrl = a.attrAsAbsoluteUrl("href"),
				rating = rating,
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
				coverUrl = it.selectFirst("img.ts-post-image")?.src(),
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}


	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)

		repeat(3) { attempt ->
			if (attempt > 0) {
				delay(500)
			}

			val docs = webClient.httpGet(chapterUrl).parseHtml()
			val pages = extractPages(docs)

			if (pages.isNotEmpty()) {
				return pages
			}
		}
		return emptyList()
	}

	private fun extractPages(docs: Document): List<MangaPage> {
		val imageSelectors = listOf(
			"div#chapter_body img",
			"img[src*='.jpg'], img[src*='.png'], img[src*='.jpeg'], img[src*='.webp']",
		)

		for (selector in imageSelectors) {
			val chapterImages = docs.select(selector)
			if (chapterImages.isNotEmpty()) {
				val pages = chapterImages.mapNotNull { img ->
					val src = img.attr("src").takeIf { it.isNotEmpty() }
						?: img.attr("data-src").takeIf { it.isNotEmpty() }
						?: img.attr("data-lazy-src").takeIf { it.isNotEmpty() }
						?: img.attr("data-original").takeIf { it.isNotEmpty() }
						?: return@mapNotNull null

					if (src.contains("loading") || src.contains("spinner") || src.contains("placeholder") || src.contains("logo")) {
						return@mapNotNull null
					}

					MangaPage(
						id = generateUid(src),
						url = if (src.startsWith("http")) src else src.toAbsoluteUrl(domain),
						preview = null,
						source = source,
					)
				}
				if (pages.isNotEmpty()) {
					return pages
				}
			}
		}
		return emptyList()
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		date ?: return 0
		return when {
			date.endsWith(" ago", ignoreCase = true) -> {
				parseRelativeDate(date)
			}

			else -> dateFormat.parseSafe(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet(
				"day",
				"days",
			).anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("hour", "hours").anyWordIn(date) -> cal.apply {
				add(
					Calendar.HOUR,
					-number,
				)
			}.timeInMillis

			WordSet(
				"mins",
			).anyWordIn(date) -> cal.apply {
				add(
					Calendar.MINUTE,
					-number,
				)
			}.timeInMillis

			WordSet("second").anyWordIn(date) -> cal.apply {
				add(
					Calendar.SECOND,
					-number,
				)
			}.timeInMillis

			WordSet("month", "months").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}
}
