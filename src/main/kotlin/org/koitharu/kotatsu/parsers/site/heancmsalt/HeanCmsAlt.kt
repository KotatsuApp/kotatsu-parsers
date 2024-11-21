package org.koitharu.kotatsu.parsers.site.heancmsalt

import org.koitharu.kotatsu.parsers.ErrorMessages
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

// Template similar to Heancms but with a different way of working

internal abstract class HeanCmsAlt(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 18,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	protected open val listUrl = "/comics"
	protected open val datePattern = "MMMM d, yyyy"

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities()

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	protected open val selectManga = "div.grid.grid-cols-2 div:not([class]):contains(M)"
	protected open val selectMangaTitle = "h5"

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append(listUrl)
			if (!filter.query.isNullOrEmpty()) {
				throw IllegalArgumentException(ErrorMessages.SEARCH_NOT_SUPPORTED)
			}
			if (page > 1) {
				append("?page=")
				append(page.toString())
			}
		}
		val doc = webClient.httpGet(url).parseHtml()

		return doc.select(selectManga).map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst(selectMangaTitle)?.text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	protected open val selectDesc = "div.description-container"
	protected open val selectAlt = "div.series-alternative-names"
	protected open val selectChapter = "ul.MuiList-root a"
	protected open val selectChapterTitle = "div.MuiListItemText-multiline span"
	protected open val selectChapterDate = "div.MuiListItemText-multiline p"

	override suspend fun getDetails(manga: Manga): Manga {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return manga.copy(
			altTitle = doc.selectFirst(selectAlt)?.text().orEmpty(),
			description = doc.selectFirst(selectDesc)?.html(),
			chapters = doc.select(selectChapter)
				.mapChapters(reversed = true) { i, a ->
					val dateText = a.selectFirst(selectChapterDate)?.text()
					val url = a.attrAsRelativeUrl("href").toAbsoluteUrl(domain)
					MangaChapter(
						id = generateUid(url),
						name = a.selectFirst(selectChapterTitle)?.text() ?: "Chapter : ${i + 1f}",
						number = i + 1f,
						volume = 0,
						url = url,
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

	protected open val selectPage = "p.flex-col.items-center img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPage).map { img ->
			val url = img.requireSrc().toRelativeUrl(domain)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	protected open fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {
			d.startsWith("hace ") || d.endsWith(" antes") -> parseRelativeDate(date)
			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()

		return when {
			WordSet("segundo").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
			WordSet("minutos", "minuto").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
			WordSet("hora", "horas").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
			WordSet("días", "día").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			WordSet("semana", "semanas").anyWordIn(date) -> cal.apply {
				add(
					Calendar.WEEK_OF_YEAR,
					-number,
				)
			}.timeInMillis

			WordSet("mes", "meses").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("año").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}
}
