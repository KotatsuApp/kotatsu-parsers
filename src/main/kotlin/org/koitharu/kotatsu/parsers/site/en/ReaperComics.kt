package org.koitharu.kotatsu.parsers.site.en

import okhttp3.Headers
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.ErrorMessages
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("REAPERCOMICS", "ReaperComics", "en")
internal class ReaperComics(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaSource.REAPERCOMICS, pageSize = 30) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.ALPHABETICAL)

	override val configKeyDomain = ConfigKey.Domain("reapercomics.com")

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_DESKTOP)
		.build()

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {

		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {
				is MangaListFilter.Search -> {
					throw IllegalArgumentException(ErrorMessages.SEARCH_NOT_SUPPORTED) // TODO
				}

				is MangaListFilter.Advanced -> {
					append("/")
					if (filter.sortOrder == SortOrder.UPDATED) {
						append("latest/")
					}
					append("comics?page=")
					append(page.toString())
				}

				null -> {
					append("/latest/comics?page=")
					append(page.toString())
				}
			}
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	private fun parseMangaList(docs: Document): List<Manga> {


		return docs.select("main div.relative, main li.col-span-1").map {
			val a = it.selectFirstOrThrow("a")
			val url = a.attrAsAbsoluteUrl("href")
			Manga(
				id = generateUid(url),
				url = url,
				title = (it.selectFirst("p a") ?: it.selectLast("a"))?.text().orEmpty(),
				altTitle = null,
				publicUrl = url,
				rating = RATING_UNKNOWN,
				isNsfw = isNsfwSource,
				coverUrl = it.selectFirstOrThrow("img").src().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> = emptySet()

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val simpleDateFormat = SimpleDateFormat("dd/MM/yyyy", sourceLocale)
		return manga.copy(
			description = doc.selectFirst("div.p-4 p.prose")?.html(),
			state = when (doc.selectFirst("dl.mt-2 div:contains(Status) dd")?.text()?.lowercase()) {
				"ongoing" -> MangaState.ONGOING
				"complete" -> MangaState.FINISHED
				else -> null
			},
			chapters = doc.select("div.p-2 div.pb-4 ul li").mapChapters(reversed = true) { i, li ->
				val a = li.selectFirstOrThrow("a")
				val chapterUrl = a.attrAsAbsoluteUrl("href").toRelativeUrl(domain)
				MangaChapter(
					id = generateUid(chapterUrl),
					name = li.selectFirst("div.truncate p.truncate")?.text().orEmpty(),
					number = i + 1,
					url = chapterUrl,
					scanlator = null,
					uploadDate = parseChapterDate(
						simpleDateFormat,
						li.selectFirst("div.truncate div.items-center")?.text(),
					),
					branch = null,
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
			WordSet("minute", "minutes").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
			WordSet("hour", "hours").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
			WordSet("day", "days").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			WordSet("week", "weeks").anyWordIn(date) -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
			WordSet("month", "months").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("img.max-w-full").map { img ->
			val url = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
