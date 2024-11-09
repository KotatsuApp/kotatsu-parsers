package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TU_MANHWAS", "TuManhwas.com", "es")
internal class TuManhwas(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.TU_MANHWAS, "tumanhwas.com", 20, 20) {

	override val listUrl = "/biblioteca"
	override val selectPage = "div#readerarea img"
	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.NEWEST)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isMultipleTagsSupported = false,
			isTagsExclusionSupported = false,
		)

	override suspend fun getFilterOptions() = super.getFilterOptions().copy(
		availableStates = emptySet(),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append(listUrl)
			append("?page=")
			append(page.toString())
			when {
				!filter.query.isNullOrEmpty() -> {
					append("&search=")
					append(filter.query.urlEncoded())
				}

				else -> {
					filter.tags.oneOrThrowIfMany()?.let {
						append("&genero=")
						append(it.key)
					}
				}
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		val chapters = docs.select(selectChapter).mapChapters(reversed = true) { index, element ->
			val url = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapChapters null
			MangaChapter(
				id = generateUid(url),
				name = element.selectFirst(".chapternum")?.textOrNull() ?: "Chapter ${index + 1}",
				url = url,
				number = index + 1f,
				volume = 0,
				scanlator = null,
				uploadDate = parseChapterDate(
					dateFormat,
					element.selectFirst(".chapterdate")?.text(),
				),
				branch = null,
				source = source,
			)
		}
		return parseInfo(docs, manga, chapters)
	}

	override suspend fun parseInfo(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {

		val stateSelect = docs.selectFirst(".tsinfo div:contains(Estado)")
		val state = stateSelect?.lastElementChild()
		val mangaState = state?.let {
			when (it.text().lowercase()) {
				"publishing" -> MangaState.ONGOING
				"terminado" -> MangaState.FINISHED
				else -> null
			}
		}
		val nsfw = docs.selectFirst(".restrictcontainer") != null
			|| docs.selectFirst(".info-right .alr") != null
			|| docs.selectFirst(".postbody .alr") != null

		return manga.copy(
			description = docs.selectFirst("div.entry-content")?.text(),
			state = mangaState,
			author = null,
			isNsfw = manga.isNsfw || nsfw,
			tags = docs.select(".wd-full .mgen > a").mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('='),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()
		val pages = ArrayList<MangaPage>()
		docs.select(selectPage).map { img ->
			val url = img.src()?.toRelativeUrl(domain)
			if (!url.isNullOrEmpty()) {
				pages.add(
					MangaPage(
						id = generateUid(url),
						url = url,
						preview = null,
						source = source,
					),
				)
			}
		}
		return pages
	}

	override suspend fun getOrCreateTagMap(): Map<String, MangaTag> = emptyMap()

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		// Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
		val d = date?.lowercase() ?: return 0
		return when {
			d.startsWith("hace") -> parseRelativeDate(date)

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
				"días",
				"día",
			).anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet(
				"hora",
				"horas",
			).anyWordIn(date) -> cal.apply {
				add(
					Calendar.HOUR,
					-number,
				)
			}.timeInMillis

			WordSet(
				"minutos",
				"minuto",
			).anyWordIn(date) -> cal.apply {
				add(
					Calendar.MINUTE,
					-number,
				)
			}.timeInMillis

			WordSet("segundo").anyWordIn(date) -> cal.apply {
				add(
					Calendar.SECOND,
					-number,
				)
			}.timeInMillis

			WordSet(
				"semana",
			).anyWordIn(date) -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis

			WordSet("mes").anyWordIn(date) -> cal.apply {
				add(
					Calendar.MONTH,
					-number,
				)
			}.timeInMillis

			WordSet("año").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}
}
