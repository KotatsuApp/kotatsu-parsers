package org.koitharu.kotatsu.parsers.site.madara.fr

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.Broken
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
import org.koitharu.kotatsu.parsers.model.YEAR_UNKNOWN
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.selectLast
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.textOrNull
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Calendar
import java.util.EnumSet
import java.util.Locale

@Broken("Needs to be fixed.")
@MangaSourceParser("RAIJINSCANS", "RaijinScans", "fr")
internal class RaijinScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.RAIJINSCANS, "raijin-scans.fr", 21) {

	override val datePattern = "dd/MM/yyyy"
	override val withoutAjax = true
	override val listUrl = ""
	override val tagPrefix = "genre/"
	override val selectBodyPage = "div.protected-image-data"
	override val selectChapter = "ul.scroll-sm li.item"
	override val selectDate = "span:nth-of-type(2)"
	override val selectPage = "div.protected-image-data"
	override val selectGenre = "div.genre-list div.genre-link"
	override val selectDesc = "div.description-content"
	override val selectState = "div.stat-item:has(span:contains(État du titre)) span.manga"

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isMultipleTagsSupported = true,
			isYearSupported = true,
			isSearchWithFiltersSupported = true,
		)

	private lateinit var tagMap: Map<String, String>

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		val availableTags = fetchAvailableTags()
		tagMap = availableTags.associateBy({ it.title }, { it.key })

		return MangaListFilterOptions(
			availableTags = availableTags,
			availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
			availableContentTypes = EnumSet.of(
				ContentType.MANGA,
				ContentType.MANHWA,
				ContentType.MANHUA,
			),
		)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://$domain")
			if (page > 0) append("/page/${page + 1}")

			append("?post_type=wp-manga&s=")
			filter.query?.let { append(it.urlEncoded()) }

			if (filter.year != YEAR_UNKNOWN) append("&release[]=${filter.year}")
			if (!filter.tags.isEmpty()) {
				append("&genre_mode=and")
				filter.tags.forEach { append("&genre[]=${it.key}") }
			}

			filter.states.forEach {
				val status = when (it) {
					MangaState.ONGOING -> "on-going"
					MangaState.FINISHED -> "end"
					else -> ""
				}
				if (status.isNotEmpty()) append("&status[]=$status")
			}

			val sortOrder = when (order) {
				SortOrder.POPULARITY -> "most_viewed"
				SortOrder.UPDATED -> "recently_added"
				SortOrder.ALPHABETICAL -> "title_az"
				else -> "recently_added"
			}
			if (sortOrder.isNotEmpty()) append("&sort=$sortOrder")
		}

		val doc = webClient.httpGet(url).parseHtml()
		return parseMangaList(doc)
	}


	override fun parseMangaList(doc: Document): List<Manga> {
		val elements = doc.select("div.original.card-lg div.unit")
		return elements.map { element ->
			val linkElement =
				element.selectFirst("a.c-title") ?: element.selectFirst("div.info > a") ?: element.selectFirst("a")
				?: error("link not found")

			val href = linkElement.attrAsRelativeUrl("href")
			val title = linkElement.text()
			val cover = element.selectLast("div.poster-image-wrapper > img")?.src()

			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = cover,
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

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()

		val title = doc.selectFirst("h1.serie-title")?.text() ?: manga.title
		val cover = doc.selectFirst("img.cover")?.src() ?: manga.coverUrl

		val author = doc.selectFirst("div.stat-item:has(span:contains(Auteur)) span.stat-value")?.text()

		val description = doc.selectFirst(selectDesc)?.text()

		val genres = doc.select(selectGenre).mapNotNullToSet { a ->
			val href = a.attr("href")
			val genreSlug = href.substringAfter("/manga-genre/").substringBefore("/").toTitleCase()
			val genreId = tagMap[genreSlug]

			if (genreId != null) {
				MangaTag(
					key = genreId,
					title = a.text(),
					source = source,
				)
			} else {
				error("Error: Genre '$genreSlug' from detail page not found in filter options map.")
			}
		}

		val state = doc.selectFirst(selectState)?.text()?.lowercase()?.let { stateText ->
			when {
				"en cours" in stateText -> MangaState.ONGOING
				"terminé" in stateText -> MangaState.FINISHED
				else -> null
			}
		}

		val rating = doc.select(".vote-count").textOrNull()?.toFloat()?.div(10f) ?: RATING_UNKNOWN

		return manga.copy(
			title = title,
			coverUrl = cover,
			authors = setOfNotNull(author),
			description = description,
			tags = genres,
			state = state,
			chapters = getChapters(manga, doc),
			rating = rating,
		)
	}

	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		return doc.select(selectChapter).mapChapters(reversed = true) { i, element ->
			val link = element.selectFirstOrThrow("a")
			val href = link.attrAsRelativeUrl("href")
			val name = link.attr("title").trim()
			val dateText = link.selectFirst(selectDate)?.text()

			MangaChapter(
				id = generateUid(href),
				title = name,
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = parseRelativeDate(dateText ?: ""),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()

		return doc.select(selectPage).map { element ->
			val encodedUrl = element.attr("data-src")
			val imageUrl = String(Base64.getDecoder().decode(encodedUrl))

			MangaPage(
				id = generateUid(imageUrl),
				url = imageUrl,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/?post_type=wp-manga&s=").parseHtml()

		return doc.select("ul.dropdown-menu.c1 li input[type=checkbox][name='genre[]']").mapNotNullToSet { input ->
			val value = input.attr("value")
			val label = input.nextElementSibling()?.text()?.trim()

			if (value.isNotEmpty() && !label.isNullOrEmpty()) {
				MangaTag(
					key = value,
					title = label.toTitleCase(),
					source = source,
				)
			} else {
				null
			}
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val lcDate = date.lowercase(Locale.FRENCH).trim()
		val cal = Calendar.getInstance()
		val number = """(\d+)""".toRegex().find(lcDate)?.value?.toIntOrNull()

		return when {
			"aujourd'hui" in lcDate -> cal.timeInMillis
			"hier" in lcDate -> cal.apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
			number != null -> when {
				("h" in lcDate || "heure" in lcDate) && "chapitre" !in lcDate -> cal.apply {
					add(
						Calendar.HOUR_OF_DAY,
						-number,
					)
				}.timeInMillis

				"min" in lcDate -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
				"jour" in lcDate || lcDate.endsWith("j") -> cal.apply {
					add(
						Calendar.DAY_OF_MONTH,
						-number,
					)
				}.timeInMillis

				"semaine" in lcDate -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
				"mois" in lcDate || (lcDate.endsWith("m") && "min" !in lcDate) -> cal.apply {
					add(
						Calendar.MONTH,
						-number,
					)
				}.timeInMillis

				"an" in lcDate -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
				else -> 0L
			}

			else -> parseChapterDate(SimpleDateFormat(datePattern, sourceLocale), date)
		}
	}
}
