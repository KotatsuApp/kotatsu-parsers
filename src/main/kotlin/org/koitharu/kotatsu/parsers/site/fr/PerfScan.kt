package org.koitharu.kotatsu.parsers.site.fr

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("PERF_SCAN", "Perf Scan", "fr")
internal class PerfScan(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.PERF_SCAN, 12) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
	)

	override val configKeyDomain = ConfigKey.Domain("perf-scan.fr")

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_DESKTOP)
		.build()

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {

		val url = buildString {
			append("https://api.$domain/query?query_string=")

			if (!query.isNullOrEmpty()) {
				append(query.urlEncoded())
			}

			append("&series_status=All&order=desc&orderBy=")
			when (sortOrder) {
				SortOrder.POPULARITY -> append("total_views")
				SortOrder.UPDATED -> append("latest")
				SortOrder.NEWEST -> append("created_at")
				SortOrder.ALPHABETICAL -> append("title")
				else -> append("latest")
			}

			append("&series_type=Comic&page=")
			append(page)
			append("&perPage=12&tags_ids=")
			append("[]".urlEncoded())
		}
		val json = webClient.httpGet(url).parseJson()
		return json.getJSONArray("data").mapJSON { j ->
			val slug = j.getString("series_slug")
			val urlManga = "https://$domain/series/$slug"
			Manga(
				id = generateUid(urlManga),
				title = j.getString("title"),
				altTitle = null,
				url = urlManga,
				publicUrl = urlManga,
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = j.getString("thumbnail"),
				tags = setOf(),
				state = when (j.getString("status")) {
					"Ongoing" -> MangaState.ONGOING
					"Completed" -> MangaState.FINISHED
					else -> null
				},
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("MM/DD/yyyy", Locale.ENGLISH)

		return manga.copy(
			altTitle = root.selectFirstOrThrow("p.text-center.text-gray-400").text(),
			tags = emptySet(),
			author = root.select("div.flex.flex-col.gap-y-2 p:contains(Autor:) strong").text(),
			description = root.selectFirst(".datas_synopsis")?.html(),
			chapters = root.select("ul.grid a")
				.mapChapters(reversed = true) { i, a ->

					val href = a.attrAsRelativeUrl("href")
					val name = a.selectFirstOrThrow("span").text()
					val dateText = a.selectLast("span")?.text() ?: "0"
					MangaChapter(
						id = generateUid(href),
						name = name,
						number = i + 1,
						url = href,
						scanlator = null,
						uploadDate = parseChapterDate(
							dateFormat,
							dateText,
						),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("p.flex img").map { img ->
			val url = img.src() ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> = emptySet()

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {
			d.endsWith(" ago") -> parseRelativeDate(date)

			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()

		return when {
			WordSet("day", "days").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			WordSet("hour", "hours").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
			WordSet("minute", "minutes").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
			WordSet("second").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
			WordSet("month", "months").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			WordSet("week").anyWordIn(date) -> cal.apply {
				add(
					Calendar.WEEK_OF_MONTH,
					-number,
				)
			}.timeInMillis

			else -> 0
		}
	}
}
