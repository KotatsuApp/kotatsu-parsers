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
class TuMangaOnlineParser(context: MangaLoaderContext) : PagedMangaParser(
	context,
	source = MangaSource.TUMANGAONLINE,
	pageSize = 24,
) {

	override val configKeyDomain = ConfigKey.Domain("lectortmo.com")

	private val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd", sourceLocale)

	override val sortOrders = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.RATING,
	)

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {

		val order =
			when (sortOrder) {
				SortOrder.POPULARITY -> "likes_count"
				SortOrder.UPDATED -> "release_date"
				SortOrder.NEWEST -> "creation"
				SortOrder.ALPHABETICAL -> "alphabetically"
				SortOrder.RATING -> "score"

			}
		val url = buildString {
			append("/library")
			if (query.isNullOrEmpty()) {
				append("?order_item=")
				append(order)
				append("&order_dir=desc")
				append("&filter_by=title")
				if (tags != null) {
					for (tag in tags) {
						append("&genders[]=${tag.key}")
					}
				}
			} else {
				append("?title=$query")
			}
			append("&_pg=1")
			append("&page=$page")
		}.toAbsoluteUrl(domain)

		val doc = webClient.httpGet(url, headers).parseHtml()
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
			largeCoverUrl = contents.selectFirst(".book-thumbnail")?.attrAsAbsoluteUrlOrNull("src"),
			state = parseStatus(contents.select("span.book-status").text().orEmpty()),
			author = contents.selectFirst("h5.card-title")?.attr("title")?.substringAfter(", "),
			chapters = if (doc.select("div.chapters").isEmpty()) {
				doc.select(oneShotChapterListSelector()).mapChapters(reversed = true) { _, item ->
					oneShotChapterFromElement(item)
				}
			} else {
				val chapters = ChaptersListBuilder(10)
				doc.select(regularChapterListSelector()).reversed().forEachIndexed { i, item ->
					val chaptername = item.select("div.col-10.text-truncate").text().replace("&nbsp;", " ").trim()
					val scanelement = item.select("ul.chapter-list > li")
					scanelement.forEach { chapters.add(regularChapterFromElement(it, chaptername, i)) }
				}
				chapters.toList()
			},
		)
	}

	private fun oneShotChapterListSelector() = "div.chapter-list-element > ul.list-group li.list-group-item"

	private fun oneShotChapterFromElement(element: Element): MangaChapter {
		val href = element.selectFirstOrThrow("div.row > .text-right > a").attrAsRelativeUrl("href")
		return MangaChapter(
			id = generateUid(href),
			name = "One Shot",
			number = 1,
			url = href,
			scanlator = element.select("div.col-md-6.text-truncate").text(),
			branch = null,
			uploadDate = chapterDateFormat.tryParse(element.select("span.badge.badge-primary.p-2").first()?.text()),
			source = source,
		)
	}

	private fun regularChapterListSelector() = "div.chapters > ul.list-group li.p-0.list-group-item"

	private fun regularChapterFromElement(element: Element, chName: String, number: Int): MangaChapter {
		val href = element.selectFirstOrThrow("div.row > .text-right > a").attrAsRelativeUrl("href")
		return MangaChapter(
			id = generateUid(href),
			name = chName,
			number = number + 1,
			url = href,
			scanlator = element.select("div.col-md-6.text-truncate").text(),
			branch = null,
			uploadDate = chapterDateFormat.tryParse(element.select("span.badge.badge-primary.p-2").first()?.text()),
			source = source,
		)
	}


	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val redirectDoc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain), headers).parseHtml()
		var doc = redirectToReadingPage(redirectDoc)
		val currentUrl = doc.location()
		val newUrl = if (!currentUrl.contains("cascade")) {
			currentUrl.substringBefore("paginated") + "cascade"
		} else {
			currentUrl
		}

		if (currentUrl != newUrl) {
			doc = webClient.httpGet(newUrl, headers).parseHtml()
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

		val redirectHeaders = Headers.Builder().set("Referer", document.baseUri()).build()

		if (script1 != null) {
			val data = script1.data()
			val regexParams = """\{uniqid:'(.+)',cascade:(.+)}""".toRegex()
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
			val regexRedirect = """window\.location\.replace\('(.+)'\)""".toRegex()
			val url = regexRedirect.find(data)!!.groupValues[1]

			return redirectToReadingPage(webClient.httpGet(url, redirectHeaders).parseHtml())
		}

		return document
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/library", headers).parseHtml()
		val elements = doc.body().select("div#books-genders > div > div")
		return elements.mapNotNullToSet { element ->
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
