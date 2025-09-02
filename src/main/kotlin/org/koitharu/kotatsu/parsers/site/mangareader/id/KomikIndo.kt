package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.Broken
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.EnumSet

@Broken
@MangaSourceParser("KOMIKINDO_MOE", "KomikIndo.org", "id", ContentType.HENTAI)
internal class KomikIndo(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.KOMIKINDO_MOE, "komikindo.ch", pageSize = 30, searchPageSize = 30) {

	override val listUrl = "/daftar-manga"
	override val selectMangaList = "div.animepost"
	override val selectMangaListImg = "div.limit img"
	override val selectMangaListTitle = "div.tt h4"
	override val selectChapter = "#chapter_list li"
	override val datePattern = "MMM d, yyyy"
	override val detailsDescriptionSelector = "div.entry-content.entry-content-single"

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isMultipleTagsSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
		),
		availableDemographics = EnumSet.of(
			Demographic.JOSEI,
			Demographic.SEINEN,
			Demographic.SHOUJO,
			Demographic.SHOUNEN,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append(listUrl)
			append("?")

			filter.tags.forEach { tag ->
				append("genre%5B%5D=")
				append(tag.key)
				append("&")
			}

			filter.demographics.forEach { demographic ->
				append("demografis%5B%5D=")
				append(
					when (demographic) {
						Demographic.JOSEI -> "josei"
						Demographic.SEINEN -> "seinen"
						Demographic.SHOUJO -> "shoujo"
						Demographic.SHOUNEN -> "shounen"
						else -> ""
					},
				)
				append("&")
			}

			filter.states.oneOrThrowIfMany()?.let { state ->
				append("status=")
				append(
					when (state) {
						MangaState.ONGOING -> "Ongoing"
						MangaState.FINISHED -> "Completed"
						else -> ""
					},
				)
				append("&")
			}

			filter.types.oneOrThrowIfMany()?.let { type ->
				append("type=")
				append(
					when (type) {
						ContentType.MANGA -> "Manga"
						ContentType.MANHWA -> "Manhwa"
						ContentType.MANHUA -> "Manhua"
						else -> ""
					},
				)
				append("&")
			}

			append("format=&")

			append("order=")
			append(
				when (order) {
					SortOrder.ALPHABETICAL -> "title"
					SortOrder.ALPHABETICAL_DESC -> "titlereverse"
					SortOrder.UPDATED -> "update"
					SortOrder.NEWEST -> "latest"
					SortOrder.POPULARITY -> "popular"
					else -> ""
				},
			)

			filter.query?.let {
				append("&title=")
				append(it.urlEncoded())
			}

			if (page > 1) {
				append("&page=")
				append(page.toString())
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return parseMangaList(doc)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)

		val chapters = docs.select(selectChapter).mapChapters(reversed = true) { index, element ->
			val a = element.selectFirst("span.lchx > a") ?: return@mapChapters null
			val url = a.attrAsRelativeUrl("href")
			val dateText = element.selectFirst("span.dt")?.text()

			MangaChapter(
				id = generateUid(url),
				title = a.text(),
				url = url,
				number = index + 1f,
				volume = 0,
				scanlator = null,
				uploadDate = parseChapterDate(dateFormat, dateText),
				branch = null,
				source = source,
			)
		}

		return parseInfo(docs, manga, chapters)
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		date ?: return 0

		return when {
			date.contains("yang lalu", ignoreCase = true) ||
				date.contains("hari ini", ignoreCase = true) ||
				date.contains("kemarin", ignoreCase = true) -> {
				parseRelativeDate(date)
			}

			else -> dateFormat.parseSafe(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: 0
		val cal = Calendar.getInstance()

		return when {
			WordSet("tahun").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			WordSet("bulan").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("minggu").anyWordIn(date) -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
			WordSet("hari").anyWordIn(date) && !date.contains("hari ini") -> cal.apply {
				add(Calendar.DAY_OF_MONTH, -number)
			}.timeInMillis

			WordSet("jam").anyWordIn(date) -> cal.apply { add(Calendar.HOUR_OF_DAY, -number) }.timeInMillis
			WordSet("menit").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
			WordSet("detik").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
			date.contains("hari ini", ignoreCase = true) -> cal.timeInMillis
			date.contains("kemarin", ignoreCase = true) -> cal.apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
			else -> 0
		}
	}

	override suspend fun parseInfo(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
		val infoElement = docs.selectFirst("div.infox")

		val altTitleElement = infoElement?.selectFirst("span:has(b:contains(Judul Alternatif))")
		val altTitles = altTitleElement?.ownText()?.trim()
			?.split(",")
			?.map { it.trim() }
			?.filter { it.isNotBlank() }
			?.toSet() ?: emptySet()

		val authorElement = infoElement?.selectFirst("span:has(b:contains(Pengarang))")
		val author = authorElement?.ownText()?.trim()

		val artistElement = infoElement?.selectFirst("span:has(b:contains(Ilustrator))")
		val artist = artistElement?.ownText()?.trim()

		val authors = listOfNotNull(author, artist).filter { it.isNotBlank() }.toSet()

		val genreTags = docs.select("div.genre-info > a").mapToSet { link ->
			val href = link.attr("href")
			val genreValue = href.substringAfterLast("/").substringBefore("?")
			MangaTag(
				key = genreValue,
				title = link.text().trim(),
				source = source,
			)
		}

		val statusElement = infoElement?.selectFirst("span:has(b:contains(Status))")
		val statusText = statusElement?.ownText()?.trim() ?: ""
		val state = when {
			statusText.contains("berjalan", true) || statusText.contains("ongoing", true) -> MangaState.ONGOING
			statusText.contains("tamat", true) || statusText.contains("completed", true) -> MangaState.FINISHED
			statusText.contains("hiatus", true) -> MangaState.PAUSED
			else -> null
		}

		val descriptionElement = docs.selectFirst(detailsDescriptionSelector)
		val description = descriptionElement?.select("p")?.text()?.trim()

		val thumbnail = docs.select(".thumb > img:nth-child(1)").attr("src").substringBeforeLast("?")

		return manga.copy(
			altTitles = altTitles,
			description = description,
			state = state,
			authors = authors,
			tags = genreTags,
			chapters = chapters,
			coverUrl = thumbnail.takeIf { it.isNotBlank() } ?: manga.coverUrl,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()

		val images = docs.select("div.img-landmine img")

		return images.map { element ->
			val url = element.attr("onError")
				.substringAfter("src='")
				.substringBefore("';")
				.takeIf { it.isNotBlank() } ?: element.attr("src")

			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain$listUrl").parseHtml()
		val tags = mutableSetOf<MangaTag>()

		doc.select("ul.dropdown-menu.c4 li input[name='genre[]']").forEach { input ->
			val value = input.attr("value")
			val label = input.nextElementSibling()?.text()
			if (value.isNotBlank() && !label.isNullOrBlank()) {
				tags.add(
					MangaTag(
						key = value,
						title = label,
						source = source,
					),
				)
			}
		}

		return tags
	}
}
