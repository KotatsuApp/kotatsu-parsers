package org.koitharu.kotatsu.parsers.site.madara.ar

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.exception.ParseException
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
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toRelativeUrl
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("ARABSHENTAI", "Arabs Hentai", "ar", ContentType.HENTAI)
internal class ArabsHentai(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ARABSHENTAI, domain = "arabshentai.com", 25) {

	override val withoutAjax = true
	override val sourceLocale: Locale = Locale("ar")
	override val listUrl = "manga/"
	override val datePattern = "yyyy-MM-dd"
	override val selectDate = ".chapterdate"
	override val selectDesc = "#manga-info .wp-content p"
	override val selectState = "#manga-info div b:contains(حالة المانجا)"
	override val selectAlt = "#manga-info div b:contains(أسماء أُخرى) + span"
	override val selectGenre = ".data .sgeneros a"
	override val selectPage = ".chapter_image img.wp-manga-chapter-img"
	override val selectChapter = "#chapter-list a[href*='/manga/'], .oneshot-reader"

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isAuthorSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(
			MangaState.ONGOING,
			MangaState.FINISHED,
			MangaState.ABANDONED,
			MangaState.PAUSED,
		),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.DOUJINSHI,
			ContentType.ONE_SHOT,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val pages = page + 1

		val url = when {
			!filter.query.isNullOrEmpty() || filter.tags.isNotEmpty() -> {
				buildString {
					append("https://")
					append(domain)
					if (pages > 1) {
						append("/page/")
						append(pages)
					}
					append("/?s=")
					append(filter.query?.urlEncoded() ?: "")

					filter.tags.forEach { tag ->
						append("&genre%5B%5D=")
						append(tag.key.urlEncoded())
					}

					if (filter.tags.size > 1) {
						append("&op=1")
					}

					filter.states.forEach { state ->
						append("&status%5B%5D=")
						append(
							when (state) {
								MangaState.ONGOING -> "on-going"
								MangaState.FINISHED -> "end"
								MangaState.ABANDONED -> "canceled"
								MangaState.PAUSED -> "on-hold"
								else -> ""
							},
						)
					}

					append("&alternative=&author=&artist=")
				}
			}

			else -> {
				buildString {
					append("https://")
					append(domain)
					append("/manga/")
					if (pages > 1) {
						append("page/")
						append(pages)
						append("/")
					}

					val params = mutableListOf<String>()

					filter.types.forEach { type ->
						params.add(
							"type=" + when (type) {
								ContentType.MANGA -> "manga"
								ContentType.MANHWA -> "manhwa"
								ContentType.MANHUA -> "manhua"
								ContentType.DOUJINSHI -> "doujinshi"
								ContentType.ONE_SHOT -> "one-shot"
								else -> "manga"
							},
						)
					}

					params.add(
						"orderby=" + when (order) {
							SortOrder.NEWEST -> "new-manga"
							SortOrder.ALPHABETICAL -> "alphabet"
							SortOrder.UPDATED -> "new_chapter"
							else -> "new_chapter"
						},
					)

					filter.states.oneOrThrowIfMany()?.let { state ->
						params.add(
							"state=" + when (state) {
								MangaState.ONGOING -> "on-going"
								MangaState.FINISHED -> "end"
								MangaState.ABANDONED -> "canceled"
								MangaState.PAUSED -> "on-hold"
								else -> ""
							},
						)
					}

					if (params.isNotEmpty()) {
						append("?")
						append(params.joinToString("&"))
					}
				}
			}
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override fun parseMangaList(doc: Document): List<Manga> {
		val searchElements = doc.select(".search-page .result-item article:not(:has(.tvshows))")

		if (searchElements.isNotEmpty()) {
			return searchElements.map { element ->
				val titleElement = element.selectFirstOrThrow(".details .title a")
				val href = titleElement.attrAsRelativeUrl("href")

				val coverUrl = element.run {
					val postId = attr("id").substringAfter("post-").ifBlank { null }
					val img = selectFirst(".image .thumbnail a img")
					val lazySrc = img?.attr("data-src")

					if (postId != null && !lazySrc.isNullOrBlank() && lazySrc.contains("/uploads/")) {
						"${lazySrc.substringBeforeLast('/')}/cover-$postId.webp"
					} else {
						img?.src()
					}
				}

				Manga(
					id = generateUid(href),
					url = href,
					publicUrl = href.toAbsoluteUrl(domain),
					title = titleElement.text(),
					coverUrl = coverUrl,
					source = source,
					contentRating = ContentRating.ADULT,
					altTitles = emptySet(),
					rating = RATING_UNKNOWN,
					tags = emptySet(),
					authors = emptySet(),
					state = null,
				)
			}
		}

		return doc.select("#archive-content .wp-manga").map { element ->
			val titleElement = element.selectFirstOrThrow(".data h3 a")
			val href = titleElement.attrAsRelativeUrl("href")

			val coverUrl = element.run {
				val postId = attr("id").substringAfter("post-").ifBlank { null }
				val img = selectFirst("a .poster img")
				val lazySrc = img?.attr("data-src")

				if (postId != null && !lazySrc.isNullOrBlank() && lazySrc.contains("/uploads/")) {
					"${lazySrc.substringBeforeLast('/')}/cover-$postId.webp"
				} else {
					img?.src()
				}
			}

			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				title = titleElement.text(),
				coverUrl = coverUrl,
				source = source,
				contentRating = ContentRating.ADULT,
				altTitles = emptySet(), rating = RATING_UNKNOWN, tags = emptySet(), authors = emptySet(), state = null,
			)
		}
	}

	override suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/تصنيفات/").parseHtml()

		return doc.select("#archive-content ul.genre-list li.item-genre .genre-data a")
			.mapNotNullToSet { a ->
				val key = a.attr("href").substringAfter(tagPrefix).removeSuffix("/")
				val title = a.ownText().trim()

				MangaTag(
					key = key,
					title = title,
					source = source,
				)
			}
	}

	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val oneshotReader = doc.selectFirst(".oneshot-reader")
		if (oneshotReader != null) {
			val firstImageLink = oneshotReader.selectFirst(".image-item a[href*='?style=paged']")
			val chapterUrl = firstImageLink?.attr("href")?.substringBeforeLast("?") ?: manga.url

			return listOf(
				MangaChapter(
					id = generateUid(chapterUrl),
					title = "ونشوت",
					number = 1f,
					volume = 0,
					url = chapterUrl.toRelativeUrl(domain),
					uploadDate = 0L,
					source = source,
					scanlator = null,
					branch = null,
				),
			)
		}

		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.select("#chapter-list a[href*='/manga/']").mapChapters(reversed = true) { i, element ->
			val href = element.attr("href")
			MangaChapter(
				id = generateUid(href),
				title = element.select(".chapternum").text().ifEmpty { "Chapter ${i + 1}" },
				number = i + 1f,
				volume = 0,
				url = href.toRelativeUrl(domain),
				uploadDate = parseChapterDate(dateFormat, element.select(selectDate).text()),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()

		val chaptersDeferred = async { getChapters(manga, doc) }

		manga.copy(
			title = doc.selectFirst(".sheader .data h1")?.text() ?: manga.title,
			coverUrl = doc.selectFirst(".sheader .poster img")?.src() ?: manga.coverUrl,
			description = doc.select(selectDesc).text(),
			altTitles = doc.select(selectAlt)
				.text()
				.split(",")
				.map { it.trim() }
				.filter { it.isNotEmpty() }
				.toSet(),
			authors = doc.select("#manga-info div b:contains(الكاتب) + span a")
				.mapNotNullToSet { it.text().takeIf { text -> text.isNotEmpty() } },
			tags = doc.select(selectGenre)
				.mapNotNullToSet { a ->
					MangaTag(
						key = a.attr("href").substringAfter(tagPrefix).removeSuffix("/"),
						title = a.text(),
						source = source,
					)
				},
			rating = doc.selectFirst(".dt_rating_vgs")?.text()?.toFloatOrNull()?.div(10f) ?: RATING_UNKNOWN,
			state = parseStatus(doc.select("#manga-info div b:contains(حالة المانجا) + span").text()),
			chapters = chaptersDeferred.await(),
		)
	}

	private fun parseStatus(status: String): MangaState? {
		return when {
			status.contains("مستمر", ignoreCase = true) -> MangaState.ONGOING
			status.contains("مكتمل", ignoreCase = true) -> MangaState.FINISHED
			status.contains("متوقف", ignoreCase = true) -> MangaState.PAUSED
			status.contains("ملغية", ignoreCase = true) -> MangaState.ABANDONED
			else -> null
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val oneshotReader = doc.selectFirst(".oneshot-reader")
		return oneshotReader?.select(".image-item img.oneshot-chapter-img")?.map { img ->
			val url = img.imgAttr() ?: throw ParseException("Image URL not found", fullUrl)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
			?: doc.select(selectPage).map { img ->
				val url = img.imgAttr() ?: throw ParseException("Image URL not found", fullUrl)
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
	}

	private fun Element.imgAttr(): String? {
		return when {
			hasAttr("data-src") -> attr("abs:data-src")
			hasAttr("src") && attr("src").isNotEmpty() -> attr("abs:src")
			hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
			hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
			hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
			hasAttr("bv-data-src") -> attr("bv-data-src")
			else -> null
		}
	}
}
