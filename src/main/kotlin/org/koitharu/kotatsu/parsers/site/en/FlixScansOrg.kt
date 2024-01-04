package org.koitharu.kotatsu.parsers.site.en

import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.ErrorMessages
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("FLIXSCANSORG", "FlixScans.org", "en")
internal class FlixScansOrg(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.FLIXSCANSORG, 18) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)
	override val availableStates: Set<MangaState> = EnumSet.allOf(MangaState::class.java)
	override val configKeyDomain = ConfigKey.Domain("flixscans.org")
	override val isSearchSupported = false

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {

		val json = when (filter) {
			is MangaListFilter.Search -> {
				throw IllegalArgumentException(ErrorMessages.SEARCH_NOT_SUPPORTED)
			}

			is MangaListFilter.Advanced -> {
				val url = buildString {
					append("https://api.")
					append(domain)
					append("/api/v1/webtoon/homepage/latest/home?page=")
					append(page.toString())
				}
				webClient.httpGet(url).parseJson().getJSONArray("data")
			}

			null -> {
				val url = "https://api.$domain/api/v1/webtoon/homepage/latest/home?page=$page"
				webClient.httpGet(url).parseJson().getJSONArray("data")
			}
		}
		return json.mapJSON { j ->
			val href = "https://$domain/series/${j.getString("prefix")}-${j.getString("id")}-${j.getString("slug")}"
			val cover = "https://media.$domain/" + j.getString("thumbnail")
			Manga(
				id = generateUid(href),
				title = j.getString("title"),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = isNsfwSource,
				coverUrl = cover,
				tags = emptySet(),
				state = when (j.getString("status")) {
					"ongoing" -> MangaState.ONGOING
					"completed" -> MangaState.FINISHED
					"onhold" -> MangaState.PAUSED
					"droped" -> MangaState.ABANDONED
					else -> null
				},
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> = emptySet()

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd/MM/yyyy", sourceLocale)
		manga.copy(
			description = doc.selectFirst("div.text-base")?.text(),
			author = doc.selectFirst("div.gap-1:contains(Authors) span.MuiChip-label")?.text(),
			altTitle = doc.select("div.gap-1:contains(Other names) span.MuiChip-label")
				.joinToString(" / ") { it.text() },
			chapters = doc.select("div.nox-scrollbar a").mapChapters(reversed = true) { i, a ->
				val url = a.attrAsRelativeUrl("href")
				val name = a.selectFirstOrThrow("div.font-medium").text()
				val dateText = a.selectLastOrThrow("div").text()
				MangaChapter(
					id = generateUid(url),
					url = url,
					name = name,
					number = i + 1,
					branch = null,
					uploadDate = parseChapterDate(
						dateFormat,
						dateText,
					),
					scanlator = null,
					source = source,
				)
			},
		)
	}

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
			WordSet("second").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
			WordSet("minute", "minutes", "mins", "min").anyWordIn(date) -> cal.apply {
				add(
					Calendar.MINUTE,
					-number,
				)
			}.timeInMillis

			WordSet("hour", "hours").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
			WordSet("day", "days").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			WordSet("week", "weeks").anyWordIn(date) -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
			WordSet("month", "months").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}


	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val urls = doc.selectFirstOrThrow("script:containsData(chapterData)").data().replace("\\", "")
			.substringAfterLast("\"webtoon\":[\"").substringBeforeLast("\"]").split("\",\"")
		return urls.map { url ->
			val urlImg = "https://media.$domain/$url"
			MangaPage(
				id = generateUid(urlImg),
				url = urlImg,
				preview = null,
				source = source,
			)
		}
	}
}
