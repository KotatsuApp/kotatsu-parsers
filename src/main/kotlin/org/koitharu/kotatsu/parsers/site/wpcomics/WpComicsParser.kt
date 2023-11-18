package org.koitharu.kotatsu.parsers.site.wpcomics

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

internal abstract class WpComicsParser(
	context: MangaLoaderContext,
	source: MangaSource,
	domain: String,
	pageSize: Int = 48,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override val isMultipleTagsSupported = false

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
	)

	protected open val listUrl = "/the-loai"
	protected open val datePattern = "dd/MM/yy"


	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}


	@JvmField
	protected val ongoing: Set<String> = setOf(
		"Đang tiến hành",
		"Ongoing",
	)

	@JvmField
	protected val finished: Set<String> = setOf(
		"Hoàn thành",
		"Completed ",
	)

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tag = tags.oneOrThrowIfMany()
		val url = buildString {
			append("https://")
			append(domain)
			append(listUrl)

			if (!tags.isNullOrEmpty()) {
				append("/")
				append(tag?.key.orEmpty())
			}

			append("?page=")
			append(page.toString())

			if (!query.isNullOrEmpty()) {
				append("&keyword=")
				append(query.urlEncoded())
			}


			append("&sort=")
			when (sortOrder) {
				SortOrder.POPULARITY -> append("10")
				SortOrder.UPDATED -> append("")
				SortOrder.NEWEST -> append("15")
				else -> append("")
			}
		}
		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("h3").text().orEmpty(),
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

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain$listUrl").parseHtml()
		return doc.select("div.genres ul li:not(.active)").mapNotNullToSet { li ->
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			val href = a.attr("href").removeSuffix('/').substringAfterLast('/')
			MangaTag(
				key = href,
				title = a.text(),
				source = source,
			)
		}
	}

	protected open val selectDesc = "div.detail-content p"
	protected open val selectState = "div.col-info li.status p:not(.name)"
	protected open val selectAut = "div.col-info li.author p:not(.name)"
	protected open val selectTag = "div.col-info li.kind p:not(.name) a"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val chaptersDeferred = async { getChapters(doc) }

		val desc = doc.selectFirstOrThrow(selectDesc).html()

		val stateDiv = doc.selectFirst(selectState)

		val state = stateDiv?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}

		val aut = doc.body().select(selectAut).text()

		manga.copy(
			tags = doc.body().select(selectTag).mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle = null,
			author = aut,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}


	protected open val selectDate = "div.col-xs-4"
	protected open val selectChapter = "div#nt_listchapter li:not(.heading)"

	protected open suspend fun getChapters(doc: Document): List<MangaChapter> {

		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.selectFirst(selectDate)?.text()

			val findHours = dateText?.contains(":")
			val dateFormat = if (findHours == true) {
				SimpleDateFormat("HH:mm dd/MM", sourceLocale)
			} else {
				SimpleDateFormat(datePattern, sourceLocale)
			}

			MangaChapter(
				id = generateUid(href),
				name = a.text(),
				number = i + 1,
				url = href,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}


	protected open val selectPage = "div.reading-detail img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()


		return doc.select(selectPage).map { url ->
			val img = url.src()?.toRelativeUrl(domain) ?: url.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(img),
				url = img,
				preview = null,
				source = source,
			)
		}
	}

	protected fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		// Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
		val d = date?.lowercase() ?: return 0
		return when {
			d.endsWith(" ago") ||
				d.endsWith(" trước")  // Handle translated 'ago' in Viêt Nam.
			-> parseRelativeDate(date)

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
				"ngày ",
			).anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("jam", "saat", "heure", "hora", "horas", "hour", "hours", "h").anyWordIn(date) -> cal.apply {
				add(
					Calendar.HOUR,
					-number,
				)
			}.timeInMillis

			WordSet(
				"min",
				"minute",
				"minutes",
				"mins",
				"phút",
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
