package org.koitharu.kotatsu.parsers.site.madara.ar

import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
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
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseFailed
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.requireSrc
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toRelativeUrl
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.util.Calendar
import java.util.EnumSet

@MangaSourceParser("ROCKSMANGA", "RocksManga", "ar")
internal class RocksManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ROCKSMANGA, "rockscans.org") {

	override val withoutAjax = false
	override val datePattern = "d MMMM yyyy"
	override val selectBodyPage = "div.reading-content"
	override val selectPage = "img"
	override val selectDesc = ".description"
	override val selectGenre = "div.genres-content a"

	// Override chapter selector for the detail page
	override val selectChapter = "ul.scroll-sm li.item"

	override val availableSortOrders: EnumSet<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.ALPHABETICAL,
		SortOrder.POPULARITY,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = emptySet(), //not supported
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED, MangaState.UPCOMING),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHUA,
			ContentType.MANHWA,
			ContentType.COMICS,
			ContentType.ONE_SHOT,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)

			if (page > 0) {
				append("/page/")
				append(page + 1)
			}

			append("/?post_type=wp-manga")

			append("&s=")
			filter.query?.let {
				append(it.urlEncoded())
			}

			filter.types.forEach { contentType ->
				val typeKey = when (contentType) {
					ContentType.MANGA -> "manga"
					ContentType.MANHUA -> "manhua"
					ContentType.MANHWA -> "manhwa"
					ContentType.COMICS -> "comic"
					ContentType.ONE_SHOT -> "one-shot"
					else -> null
				}
				typeKey?.let {
					append("&type[]=")
					append(it)
				}
			}

			filter.states.forEach { state ->
				val statusKey = when (state) {
					MangaState.ONGOING -> "on-going"
					MangaState.FINISHED -> "end"
					MangaState.PAUSED -> "on-hold"
					MangaState.UPCOMING -> "upcoming"
					else -> null
				}
				statusKey?.let {
					append("&status[]=")
					append(it)
				}
			}

			append("&sort=")
			when (order) {
				SortOrder.ALPHABETICAL -> append("title_az")
				SortOrder.POPULARITY -> append("most_viewed")
				SortOrder.UPDATED, SortOrder.NEWEST -> append("recently_added")
				else -> append("recently_added")
			}
		}

		val doc = webClient.httpGet(url).parseHtml()

		// Check if we got redirected to the main page (no results)
		val currentUrl = doc.location()
		if (currentUrl == "https://$domain/" || currentUrl == "https://$domain") {
			return emptyList()
		}

		return parseMangaList(doc)
	}

	override fun parseMangaList(doc: Document): List<Manga> {
		val items = doc.select("div.original.card-lg div.unit")

		return items.map { unit ->
			val posterLink = unit.selectFirstOrThrow("a.poster")
			val href = posterLink.attr("href").toRelativeUrl(domain)

			val img = posterLink.selectFirst("img")

			val info = unit.selectFirst("div.info")
			val titleLink = info?.selectFirst("a")
			val title = titleLink?.text()?.trim() ?: "Unknown"

			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = img?.src(),
				title = title,
				altTitles = emptySet(),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				authors = emptySet(),
				state = null,
				source = source,
				contentRating = ContentRating.SAFE,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val title = doc.selectFirst("div.info h1")?.text() ?: manga.title

		val coverUrl = doc.selectFirst("div.poster img")?.src() ?: manga.coverUrl

		val description = doc.selectFirst("div.description")?.html()?.takeIf { it.isNotBlank() }

		val metaElements = doc.select("div.meta > div")
		var author: String? = null
		var status: MangaState? = null
		val altTitles = mutableSetOf<String>()

		metaElements.forEach { element ->
			val label = element.selectFirst("span")?.text()?.trim() ?: return@forEach
			val value = element.selectFirst("a")?.text()?.trim() ?: element.ownText().trim()

			when {
				label.contains("المؤلف") || label.contains("الكاتب") -> author = value
				label.contains("الحالة") || label.contains("الوضع") -> {
					status = when (value.lowercase()) {
						"مستمر", "مستمرة", "ongoing" -> MangaState.ONGOING
						"مكتمل", "مكتملة", "completed", "complete" -> MangaState.FINISHED
						"متوقف", "متوقفة", "hiatus" -> MangaState.PAUSED
						"ملغي", "ملغية", "cancelled", "dropped" -> MangaState.ABANDONED
						else -> null
					}
				}

				label.contains("الأسماء البديلة") || label.contains("أسماء أخرى") -> {
					value.split(",", "،", ";").forEach { name ->
						val trimmedName = name.trim()
						if (trimmedName.isNotEmpty()) {
							altTitles.add(trimmedName)
						}
					}
				}
			}
		}

		val tags = doc.select("div.genres a, div.tags a").mapNotNullToSet { a ->
			val href = a.attr("href").removeSuffix("/").substringAfterLast("/")
			val name = a.text().trim()
			if (href.isNotEmpty() && name.isNotEmpty()) {
				MangaTag(
					key = href,
					title = name,
					source = source,
				)
			} else null
		}

		val ratingText = doc.selectFirst("div.rating span.score")?.text()
		val rating = ratingText?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN

		val chapters = getChapters(manga, doc)

		val isAdult = doc.selectFirst("div.adult-content") != null ||
			tags.any { it.key in setOf("adult", "mature", "18+", "ecchi", "smut") }

		manga.copy(
			title = title,
			altTitles = manga.altTitles + altTitles,
			coverUrl = coverUrl,
			largeCoverUrl = coverUrl,
			description = description,
			tags = tags,
			state = status,
			authors = setOfNotNull(author),
			rating = rating,
			chapters = chapters,
			contentRating = if (isAdult) ContentRating.ADULT else ContentRating.SAFE,
			publicUrl = fullUrl,
		)
	}

	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		return doc.body().select("ul.scroll-sm li.item").mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attr("href").toRelativeUrl(domain)
			val link = href + stylePage

			val chapterText = a.attr("title").takeIf { it.isNotBlank() }
				?: a.selectFirst("span.contain-zeb")?.text()
				?: a.ownText()
			val name = chapterText.replace("الفصل", "Chapter").trim()

			val dateText = li.selectFirst("span.time")?.text().orEmpty()

			val scanlator = li.selectFirst("span.user span")?.text()

			MangaChapter(
				id = generateUid(href),
				url = link,
				title = name,
				number = i + 1f,
				volume = 0,
				branch = null,
				uploadDate = parseRelativeDate(dateText),
				scanlator = scanlator,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val pageContainerSelector = "div#ch-images"
		val imageSelector = "img.preload-image"

		val container = doc.selectFirst(pageContainerSelector)
			?: doc.parseFailed("Page container '$pageContainerSelector' not found on page: $fullUrl")

		val imageElements = container.select(imageSelector)

		if (imageElements.isEmpty()) {
			doc.parseFailed("No images found with selector '$imageSelector' in container.")
		}

		return imageElements.map { imgElement ->
			val absoluteImageUrl = imgElement.requireSrc()

			val relativeUrl = absoluteImageUrl.toRelativeUrl(domain)

			MangaPage(
				id = generateUid(relativeUrl),
				url = relativeUrl,
				preview = null,
				source = source,
			)
		}
	}

	private fun parseRelativeDate(dateText: String): Long {
		if (dateText.isEmpty()) return 0
		val cleanText = dateText.replace("منذ", "").trim()
		val cal = Calendar.getInstance()

		if (cleanText.startsWith("لحظات") || cleanText.startsWith("لحظة")) return System.currentTimeMillis()
		if (cleanText.startsWith("ساعة")) return cal.apply { add(Calendar.HOUR_OF_DAY, -1) }.timeInMillis
		if (cleanText.contains("يومين")) return cal.apply { add(Calendar.DAY_OF_MONTH, -2) }.timeInMillis
		if (cleanText.startsWith("يوم")) return cal.apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
		if (cleanText.startsWith("أسبوع")) return cal.apply { add(Calendar.WEEK_OF_YEAR, -1) }.timeInMillis
		if (cleanText.startsWith("شهر")) return cal.apply { add(Calendar.MONTH, -1) }.timeInMillis
		if (cleanText.startsWith("سنة")) return cal.apply { add(Calendar.YEAR, -1) }.timeInMillis

		val number = Regex("""(\d+)""").find(cleanText)?.value?.toIntOrNull() ?: return 0

		return when {
			cleanText.contains("ساعة") || cleanText.contains("ساعات") -> cal.apply {
				add(
					Calendar.HOUR_OF_DAY,
					-number,
				)
			}.timeInMillis

			cleanText.contains("أيام") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			cleanText.contains("أسبوع") || cleanText.contains("أسابيع") -> cal.apply {
				add(
					Calendar.WEEK_OF_YEAR,
					-number,
				)
			}.timeInMillis

			cleanText.contains("شهر") || cleanText.contains("أشهر") -> cal.apply {
				add(
					Calendar.MONTH,
					-number,
				)
			}.timeInMillis

			cleanText.contains("سنة") || cleanText.contains("سنوات") -> cal.apply {
				add(
					Calendar.YEAR,
					-number,
				)
			}.timeInMillis

			else -> 0
		}
	}
}
