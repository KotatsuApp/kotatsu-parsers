package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@MangaSourceParser("MANHWA_FREAK_FR", "ManhwaFreak", "fr")
internal class ManhwaFreakFr(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHWA_FREAK_FR, "manhwafreak.fr", pageSize = 0, searchPageSize = 10) {

	override val selectMangaList = ".listupd .lastest-serie"
	override val selectMangaListImg = "img"
	override val sourceLocale: Locale = Locale.ENGLISH

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (!query.isNullOrEmpty()) {
			if (page > lastSearchPage) {
				return emptyList()
			}

			val url = buildString {
				append("https://")
				append(domain)
				append("/page/")
				append(page)
				append("/?s=")
				append(query.urlEncoded())
			}

			val docs = webClient.httpGet(url).parseHtml()
			lastSearchPage = docs.selectFirst(".pagination .next")
				?.previousElementSibling()
				?.text()?.toIntOrNull() ?: 1
			return parseMangaList(docs)
		}

		if (!tags.isNullOrEmpty()) {

			if (page > 1) {
				return emptyList()
			}

			val tag = tags.oneOrThrowIfMany()
			val url = buildString {
				append("https://")
				append(domain)
				append("/genres/?genre=")
				append(tag?.key.orEmpty())
			}

			return parseMangaList(webClient.httpGet(url).parseHtml())
		}

		if (page > 1) {
			return emptyList()
		}
		val sortQuery = when (sortOrder) {
			SortOrder.ALPHABETICAL -> "az"
			SortOrder.NEWEST -> "new"
			SortOrder.POPULARITY -> "views"
			SortOrder.UPDATED -> ""
			else -> ""
		}

		val url = buildString {
			append("https://")
			append(domain)
			append(listUrl)
			append("/?order=")
			append(sortQuery)
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/genres/").parseHtml()

		return doc.select("ul.genre-list li a").mapNotNullToSet { a ->
			val href = a.attr("href").substringAfterLast("=")
			MangaTag(
				key = href,
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		val chapters = docs.select("div.chapter-li a").mapChapters(reversed = true) { index, a ->
			val url = a.attrAsRelativeUrl("href")
			val dateText = a.selectFirst(".chapter-info p.new")?.text() ?: a.select(".chapter-info p")[1].text()
			MangaChapter(
				id = generateUid(url),
				name = a.selectFirst(".chapter-info p:contains(Chapter)")?.text() ?: "Chapter ${index + 1}",
				url = url,
				number = index + 1,
				scanlator = null,
				uploadDate = if (dateText == "NEW") {
					parseChapterDate(
						dateFormat,
						"today",
					)
				} else {
					parseChapterDate(
						dateFormat,
						dateText,
					)
				},
				branch = null,
				source = source,
			)
		}
		return parseInfo(docs, manga, chapters)
	}

	override suspend fun parseInfo(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {

		val tagMap = getOrCreateTagMap()
		val selectTag = docs.requireElementById("info").select("div:contains(Genre) > p:last-child").text().split(",")
		val tags = selectTag.mapNotNullToSet { tagMap[it] }

		val mangaState = docs.requireElementById("info").select("div:contains(Status) > p:last-child").text().let {
			when (it) {
				"Ongoing" -> MangaState.ONGOING
				"Completed" -> MangaState.FINISHED
				else -> null
			}
		}
		val author = docs.requireElementById("info").select("div:contains(Author(s)) > p:last-child").text()

		return manga.copy(
			altTitle = docs.requireElementById("info").select("div:contains(Alternative) > p:last-child").text(),
			description = docs.requireElementById("summary").html(),
			state = mangaState,
			author = author,
			isNsfw = manga.isNsfw,
			tags = tags,
			chapters = chapters,
		)
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		// Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
		val d = date?.lowercase() ?: return 0
		return when {
			d.endsWith(" ago") -> parseRelativeDate(date)
			// Handle 'yesterday' and 'today', using midnight
			d.startsWith("year") -> Calendar.getInstance().apply {
				add(Calendar.DAY_OF_MONTH, -1) // yesterday
				set(Calendar.HOUR_OF_DAY, 0)
				set(Calendar.MINUTE, 0)
				set(Calendar.SECOND, 0)
				set(Calendar.MILLISECOND, 0)
			}.timeInMillis

			d.startsWith("today") -> Calendar.getInstance().apply {
				set(Calendar.HOUR_OF_DAY, 0)
				set(Calendar.MINUTE, 0)
				set(Calendar.SECOND, 0)
				set(Calendar.MILLISECOND, 0)
			}.timeInMillis

			date.contains(Regex("""\d(st|nd|rd|th)""")) -> date.split(" ").map {
				if (it.contains(Regex("""\d\D\D"""))) {
					it.replace(Regex("""\D"""), "")
				} else {
					it
				}
			}.let { dateFormat.tryParse(it.joinToString(" ")) }

			else -> dateFormat.tryParse(date)
		}
	}

	// Parses dates in this form:
	// 21 hours ago
	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()

		return when {
			WordSet(
				"day",
				"days",
				"d",
			).anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet(
				"hour",
				"hours",
				"h",
			).anyWordIn(date) -> cal.apply {
				add(
					Calendar.HOUR,
					-number,
				)
			}.timeInMillis

			WordSet(
				"minute",
				"minutes",
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
