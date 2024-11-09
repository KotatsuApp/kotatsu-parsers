package org.koitharu.kotatsu.parsers.site.es

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TUMANGAONLINE", "TuMangaOnline", "es")
internal class TuMangaOnlineParser(context: MangaLoaderContext) : PagedMangaParser(
	context,
	source = MangaParserSource.TUMANGAONLINE,
	pageSize = 24,
) {

	override val configKeyDomain = ConfigKey.Domain("zonatmo.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	private val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd", sourceLocale)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.RATING,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isTagsExclusionSupported = true,
			isMultipleTagsSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableContentRating = EnumSet.of(ContentRating.SAFE, ContentRating.ADULT),
		availableDemographics = EnumSet.of(
			Demographic.SHOUNEN,
			Demographic.SHOUJO,
			Demographic.SEINEN,
			Demographic.JOSEI,
			Demographic.KODOMO,
		),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.NOVEL,
			ContentType.ONE_SHOT,
			ContentType.DOUJINSHI,
			ContentType.OTHER,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/library?_pg=1&page=")
			append(page.toString())

			filter.query?.let {
				append("&title=")
				append(filter.query.urlEncoded())
			}

			append("&order_item=")
			append(
				when (order) {
					SortOrder.POPULARITY -> "likes_count&order_dir=desc"
					SortOrder.POPULARITY_ASC -> "likes_count&order_dir=asc"
					SortOrder.UPDATED -> "release_date&order_dir=desc"
					SortOrder.UPDATED_ASC -> "release_date&order_dir=asc"
					SortOrder.NEWEST -> "creation&order_dir=desc"
					SortOrder.NEWEST_ASC -> "creation&order_dir=asc"
					SortOrder.ALPHABETICAL -> "alphabetically&order_dir=asc"
					SortOrder.ALPHABETICAL_DESC -> "alphabetically&order_dir=desc"
					SortOrder.RATING -> "score&order_dir=desc"
					SortOrder.RATING_ASC -> "score&order_dir=asc"
					else -> "release_date&order_dir=desc"
				},
			)
			append("&filter_by=title")

			filter.tags.forEach {
				append("&genders[]=")
				append(it.key)
			}

			filter.tagsExclude.forEach {
				append("&exclude_genders[]=")
				append(it.key)
			}

			append("&type=")
			filter.types.forEach {
				append(
					when (it) {
						ContentType.MANGA -> "manga"
						ContentType.MANHWA -> "manhwa"
						ContentType.MANHUA -> "manhua"
						ContentType.NOVEL -> "novel"
						ContentType.ONE_SHOT -> "one_shot"
						ContentType.DOUJINSHI -> "doujinshi"
						ContentType.OTHER -> "oel"
						else -> ""
					},
				)
			}

			filter.demographics.forEach {
				append("&demography=")
				append(
					when (it) {
						Demographic.SHOUNEN -> "shounen"
						Demographic.SHOUJO -> "shoujo"
						Demographic.SEINEN -> "seinen"
						Demographic.JOSEI -> "josei"
						Demographic.KODOMO -> "kodomo"
						else -> ""
					},
				)
			}

			filter.contentRating.oneOrThrowIfMany()?.let {
				append("&erotic=")
				append(
					when (it) {
						ContentRating.SAFE -> "false"
						ContentRating.ADULT -> "true"
						else -> ""
					},
				)
			}
		}
		val doc = webClient.httpGet(url, getRequestHeaders()).parseHtml()
		val items = doc.body().select("div.element")
		return items.mapNotNull { item ->
			val href =
				item.selectFirst("a")?.attrAsRelativeUrlOrNull("href")?.substringAfter(' ') ?: return@mapNotNull null
			Manga(
				id = generateUid(href),
				title = item.selectFirst("h4.text-truncate")?.text() ?: return@mapNotNull null,
				coverUrl = item.select("style").toString().substringAfter("('").substringBeforeLast("')"),
				altTitle = null,
				author = null,
				rating = item.selectFirst("span.score")?.text()?.toFloatOrNull()?.div(10F) ?: RATING_UNKNOWN,
				url = href,
				isNsfw = item.select("i").hasClass("fas fa-heartbeat fa-2x"),
				tags = emptySet(),
				state = null,
				publicUrl = href.toAbsoluteUrl(doc.host ?: domain),
				source = source,
			)
		}

	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val contents = doc.body().selectFirstOrThrow("section.element-header-content")
		return manga.copy(
			description = contents.selectFirst("p.element-description")?.html(),
			tags = contents.select("h6 a").mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringBefore('&').substringAfterLast('='),
					title = a.text(),
					source = source,
				)
			},
			largeCoverUrl = contents.selectFirst(".book-thumbnail")?.attrAsAbsoluteUrlOrNull("src"),
			state = parseStatus(contents.select("span.book-status").text().orEmpty()),
			author = contents.selectFirst("h5.card-title")?.attr("title")?.substringAfter(", "),
			chapters = if (doc.select("div.chapters").isEmpty()) {
				doc.select(oneShotChapterListSelector).mapChapters(reversed = true) { _, item ->
					oneShotChapterFromElement(item)
				}
			} else {
				val chapters = ChaptersListBuilder(10)
				doc.select(regularChapterListSelector).reversed().forEachIndexed { i, item ->
					val chapterName = item.select("div.col-10.text-truncate").text().replace("&nbsp;", " ").trim()
					val scanElement = item.select("ul.chapter-list > li")
					scanElement.forEach { chapters.add(regularChapterFromElement(it, chapterName, i)) }
				}
				chapters.toList()
			},
		)
	}

	private val oneShotChapterListSelector = "div.chapter-list-element > ul.list-group li.list-group-item"

	private fun oneShotChapterFromElement(element: Element): MangaChapter {
		val href = element.selectFirstOrThrow("div.row > .text-right > a").attrAsRelativeUrl("href")
		return MangaChapter(
			id = generateUid(href),
			name = "One Shot",
			number = 1f,
			volume = 0,
			url = href,
			scanlator = element.select("div.col-md-6.text-truncate").text(),
			branch = null,
			uploadDate = chapterDateFormat.tryParse(element.select("span.badge.badge-primary.p-2").first()?.text()),
			source = source,
		)
	}

	private val regularChapterListSelector = "div.chapters > ul.list-group li.p-0.list-group-item"

	private fun regularChapterFromElement(element: Element, chName: String, number: Int): MangaChapter {
		val href = element.selectFirstOrThrow("div.row > .text-right > a").attrAsRelativeUrl("href")
		return MangaChapter(
			id = generateUid(href),
			name = chName,
			number = number + 1f,
			volume = 0,
			url = href,
			scanlator = element.select("div.col-md-6.text-truncate").text(),
			branch = null,
			uploadDate = chapterDateFormat.tryParse(element.select("span.badge.badge-primary.p-2").first()?.text()),
			source = source,
		)
	}


	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val redirectDoc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain), getRequestHeaders()).parseHtml()
		var doc = redirectToReadingPage(redirectDoc)
		val currentUrl = doc.location()
		val newUrl = if (!currentUrl.contains("cascade")) {
			currentUrl.substringBefore("paginated") + "cascade"
		} else {
			currentUrl
		}

		if (currentUrl != newUrl) {
			doc = webClient.httpGet(newUrl, getRequestHeaders()).parseHtml()
		}

		return doc.select("div.viewer-container img:not(noscript img)").map {
			val href = if (it.hasAttr("data-src")) {
				it.attr("abs:data-src")
			} else {
				it.attr("abs:src")
			}
			MangaPage(
				id = generateUid(href),
				url = href,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun redirectToReadingPage(document: Document): Document {
		val script1 = document.selectFirst("script:containsData(uniqid)")
		val script2 = document.selectFirst("script:containsData(window.location.replace)")
		val script3 = document.selectFirst("script:containsData(redirectUrl)")
		val script4 = document.selectFirst("input#redir")
		val script5 = document.selectFirst("script:containsData(window.opener):containsData(location.replace)")

		val redirectHeaders = Headers.Builder().set("Referer", document.baseUri()).build()

		if (script1 != null) {
			val data = script1.data()

			@Suppress("RegExpRedundantEscape")
			val regexParams = """\{uniqid:\s*'(\S+)',\s*cascade:\s*(\S+)\}""".toRegex()
			val regexAction = """form\.action\s?=\s?'(.+)'""".toRegex()
			val params = regexParams.find(data)!!
			val action = regexAction.find(data)!!.groupValues[1].toHttpUrl()

			val formBody = mapOf(
				"uniqid" to params.groupValues[1],
				"cascade" to params.groupValues[2],
			)
			return redirectToReadingPage(webClient.httpPost(action, formBody, redirectHeaders).parseHtml())
		}

		if (script2 != null) {
			val data = script2.data()
			val regexRedirect = """window\.location\.replace\(['"](.+)['"]\)""".toRegex()
			val url = regexRedirect.find(data)?.groupValues?.get(1)?.unescapeUrl()

			if (url != null) {
				return redirectToReadingPage(webClient.httpGet(url, redirectHeaders).parseHtml())
			}
		}

		if (script3 != null) {
			val data = script3.data()
			val regexRedirect = """redirectUrl\s*=\s*'(.+)'""".toRegex()
			val url = regexRedirect.find(data)?.groupValues?.get(1)?.unescapeUrl()

			if (url != null) {
				return redirectToReadingPage(webClient.httpGet(url, redirectHeaders).parseHtml())
			}
		}

		if (script4 != null) {
			val url = script4.attr("value").unescapeUrl()

			return redirectToReadingPage(webClient.httpGet(url, redirectHeaders).parseHtml())
		}

		if (script5 != null) {
			val data = script5.data()
			val regexRedirect = """;[^.]location\.replace\(['"](.+)['"]\)""".toRegex()
			val url = regexRedirect.find(data)?.groupValues?.get(1)?.unescapeUrl()

			if (url != null) {
				return redirectToReadingPage(webClient.httpGet(url, redirectHeaders).parseHtml())
			}
		}

		return document
	}

	private fun String.unescapeUrl(): String {
		return if (this.startsWith("http:\\/\\/") || this.startsWith("https:\\/\\/")) {
			this.replace("\\/", "/")
		} else {
			this
		}
	}


	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/library", getRequestHeaders()).parseHtml()
		val elements = doc.body().select("div#books-genders > div > div")
		return elements.mapToSet { element ->
			MangaTag(
				title = element.select("label").text(),
				key = element.select("input").attr("value"),
				source = source,
			)
		}
	}

	private fun parseStatus(status: String) = when {
		status.contains("Publicándose") -> MangaState.ONGOING
		status.contains("Finalizado") -> MangaState.FINISHED
		else -> null
	}
}
