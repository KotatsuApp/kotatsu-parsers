package org.koitharu.kotatsu.parsers.site.id

import okhttp3.Headers
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
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
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.requireSrc
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toRelativeUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("IKIRU", "Ikiru", "id")
internal class Ikiru(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.IKIRU, 24, 24) {

	override val configKeyDomain = ConfigKey.Domain("01.ikiru.wtf")
	override val sourceLocale: Locale = Locale.ENGLISH

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
		SortOrder.RATING,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = false,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.COMICS,
			ContentType.NOVEL,
		),
	)

	private var nonce: String? = null

	private suspend fun getNonce(): String {
		if (nonce == null) {
			val json =
				webClient.httpGet("https://${domain}/ajax-call?type=search_form&action=get_nonce")
			val html = json.parseHtml()
			val nonceValue = html.select("input[name=search_nonce]").attr("value")
			nonce = nonceValue
		}
		return nonce!!
	}


	override suspend fun getListPage(
		page: Int,
		order: SortOrder,
		filter: MangaListFilter,
	): List<Manga> {
		val url = "https://${domain}/ajax-call"

		val formParts = mutableMapOf<String, String>()
		formParts["action"] = "advanced_search"
		formParts["page"] = page.toString()
		formParts["nonce"] = getNonce()

		filter.query?.let { formParts["query"] = it }

		if (filter.tags.isNotEmpty()) {
			formParts["genre-relation"] = "AND"
			val genreArray = JSONArray(filter.tags.map { it.key })
			formParts["genre"] = genreArray.toString()
		}

		if (filter.types.isNotEmpty()) {
			val typeArray = JSONArray()
			filter.types.forEach { type ->
				when (type) {
					ContentType.MANGA -> typeArray.put("manga")
					ContentType.MANHWA -> typeArray.put("manhwa")
					ContentType.MANHUA -> typeArray.put("manhua")
					ContentType.COMICS -> typeArray.put("comic")
					ContentType.NOVEL -> typeArray.put("novel")
					else -> {}
				}
			}
			if (typeArray.length() > 0) {
				formParts["type"] = typeArray.toString()
			}
		}

		if (filter.states.isNotEmpty()) {
			val statusArray = JSONArray()
			filter.states.forEach { state ->
				when (state) {
					MangaState.ONGOING -> statusArray.put("ongoing")
					MangaState.FINISHED -> statusArray.put("completed")
					MangaState.PAUSED -> statusArray.put("on-hiatus")
					else -> {}
				}
			}
			if (statusArray.length() > 0) {
				formParts["status"] = statusArray.toString()
			}
		}

		if (!filter.author.isNullOrEmpty()) {
			val authorArray = JSONArray(filter.author)
			formParts["series-author"] = authorArray.toString()
		}

		formParts["orderby"] = when (order) {
			SortOrder.UPDATED -> "updated"
			SortOrder.POPULARITY -> "popular"
			SortOrder.ALPHABETICAL -> "title"
			SortOrder.RATING -> "rating"
			else -> "popular"
		}


		val html = webClient.httpPost(url, form = formParts).parseHtml()
		return parseMangaList(html)
	}

	private fun parseMangaList(doc: Document): List<Manga> {
		val mangaList = mutableListOf<Manga>()

		doc.select("body > div").forEach { divElement ->
			val mainLink = divElement.selectFirst("a[href*='/manga/']") ?: return@forEach
			val href = mainLink.attrAsRelativeUrl("href")

			if (href.contains("/chapter-")) return@forEach

			val title = divElement.selectFirst("a.text-base, a.text-white, h1")?.text()?.trim()
				?: mainLink.attr("title").ifEmpty { mainLink.text() }

			val coverUrl = divElement.selectFirst("img")?.src()

			val ratingText = divElement.selectFirst(".numscore, span.text-yellow-400")?.text()
			val rating = ratingText?.toFloatOrNull()?.let {
				if (it > 5) it / 10f else it / 5f
			} ?: RATING_UNKNOWN

			val stateText =
				divElement.selectFirst("span.bg-accent, p:contains(Ongoing), p:contains(Completed)")
					?.text()?.lowercase()
			val state = when {
				stateText?.contains("ongoing") == true -> MangaState.ONGOING
				stateText?.contains("completed") == true -> MangaState.FINISHED
				stateText?.contains("hiatus") == true -> MangaState.PAUSED
				else -> null
			}

			mangaList.add(
				Manga(
					id = generateUid(href),
					url = href,
					title = title,
					altTitles = emptySet(),
					publicUrl = mainLink.attrAsAbsoluteUrl("href"),
					rating = rating,
					contentRating = if (isNsfwSource) ContentRating.ADULT else null,
					coverUrl = coverUrl,
					tags = emptySet(),
					state = state,
					authors = emptySet(),
					source = source,
				),
			)
		}

		return mangaList
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()

		// Manga ID for chapter loading
		val mangaId = doc.selectFirst("[hx-get*='manga_id=']")
			?.attr("hx-get")
			?.substringAfter("manga_id=")
			?.substringBefore("&")
			?.trim()
			?: doc.selectFirst("input#manga_id, [data-manga-id]")
				?.let { it.attr("value").ifEmpty { it.attr("data-manga-id") } }
			?: manga.url.substringAfterLast("/manga/").substringBefore("/")

		val titleElement = doc.selectFirst("h1[itemprop=name]")
		val title = titleElement?.text() ?: manga.title

		val altTitles = titleElement?.nextElementSibling()?.text()
			?.split(',')
			?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
			?.toSet()
			?: emptySet()

		val description = doc.select("div[itemprop=description]")
			.joinToString("\n\n") { it.text() }
			.trim()
			.takeIf { it.isNotBlank() }

		val coverUrl = doc.selectFirst("div[itemprop=image] > img")?.src()
			?: manga.coverUrl

		val tags = doc.select("a[itemprop=genre]").mapNotNullToSet { a ->
			MangaTag(
				key = a.attr("href").substringAfterLast("/genre/").removeSuffix("/"),
				title = a.text().toTitleCase(),
				source = source,
			)
		}

		fun findInfoText(key: String): String? {
			return doc.select("div.space-y-2 > .flex:has(h4)")
				.find { it.selectFirst("h4")?.text()?.contains(key, ignoreCase = true) == true }
				?.selectFirst("p.font-normal")?.text()
		}

		val stateText = findInfoText("Status")?.lowercase()
		val state = when {
			stateText?.contains("ongoing") == true -> MangaState.ONGOING
			stateText?.contains("completed") == true -> MangaState.FINISHED
			stateText?.contains("hiatus") == true -> MangaState.PAUSED
			else -> manga.state
		}

		val authors = findInfoText("Author")
			?.split(",")
			?.map { it.trim() }
			?.toSet() ?: emptySet()

		val chapters = loadChapters(mangaId, manga.url.toAbsoluteUrl(domain))

		return manga.copy(
			title = title,
			altTitles = altTitles,
			description = description,
			coverUrl = coverUrl,
			tags = tags,
			state = state,
			authors = authors,
			chapters = chapters,
		)
	}

	private suspend fun loadChapters(
		mangaId: String,
		mangaAbsoluteUrl: String,
	): List<MangaChapter> {
		val chapters = mutableListOf<MangaChapter>()
		var page = 1

		val headers = Headers.Companion.headersOf(
			"hx-request", "true",
			"Referer", mangaAbsoluteUrl,
		)

		while (true) {
			val url = "https://${domain}/ajax-call?manga_id=$mangaId&page=$page&action=chapter_list"
			val doc = webClient.httpGet(url, headers).parseHtml()

			val chapterElements = doc.select("div#chapter-list > div[data-chapter-number]")
			if (chapterElements.isEmpty()) break

			chapterElements.forEach { element ->
				val a = element.selectFirst("a") ?: return@forEach
				val href = a.attrAsRelativeUrl("href")
				if (href.isBlank()) return@forEach

				val chapterTitle = element.selectFirst("div.font-medium span")?.text()?.trim() ?: ""
				val dateText = element.selectFirst("time")?.text()
				val number = element.attr("data-chapter-number").toFloatOrNull() ?: -1f

				chapters.add(
					MangaChapter(
						id = generateUid(href),
						title = chapterTitle,
						url = href,
						number = number,
						volume = 0,
						scanlator = null,
						uploadDate = parseDate(dateText),
						branch = null,
						source = source,
					),
				)
			}
			page++
			if (page > 100) break
		}
		return chapters.reversed()
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("main section section > img").map { img ->
			val url = img.requireSrc().toRelativeUrl(domain)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://${domain}/advanced-search/").parseHtml()

		return doc.select("[data-genre], .genre-item").mapNotNullToSet { element ->
			val key = element.attr("data-genre").ifEmpty {
				element.selectFirst("input")?.attr("value")
			} ?: return@mapNotNullToSet null

			val title = element.text().ifEmpty {
				element.selectFirst("label")?.text()
			} ?: return@mapNotNullToSet null

			MangaTag(
				key = key,
				title = title.toTitleCase(),
				source = source,
			)
		}
	}


	private fun parseDate(dateStr: String?): Long {
		if (dateStr.isNullOrEmpty()) return 0

		return try {
			when {
				dateStr.contains("ago") -> {
					val number = Regex("""(\d+)""").find(dateStr)?.value?.toIntOrNull() ?: return 0
					val cal = Calendar.getInstance()
					when {
						dateStr.contains("min") -> cal.apply { add(Calendar.MINUTE, -number) }
						dateStr.contains("hour") -> cal.apply { add(Calendar.HOUR, -number) }
						dateStr.contains("day") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }
						dateStr.contains("week") -> cal.apply {
							add(
								Calendar.WEEK_OF_YEAR,
								-number,
							)
						}

						dateStr.contains("month") -> cal.apply { add(Calendar.MONTH, -number) }
						dateStr.contains("year") -> cal.apply { add(Calendar.YEAR, -number) }
						else -> cal
					}.timeInMillis
				}

				else -> {
					SimpleDateFormat("MMM dd, yyyy", sourceLocale).parse(dateStr)?.time ?: 0
				}
			}
		} catch (_: Exception) {
			0
		}
	}
}
