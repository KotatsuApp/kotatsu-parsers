package org.koitharu.kotatsu.parsers.site.fmreader.ja

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.fmreader.FmreaderParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat

@MangaSourceParser("KLZ9", "Klz9", "ja")
internal class Klz9(context: MangaLoaderContext) :
	FmreaderParser(context, MangaParserSource.KLZ9, "klz9.com") {

	override val selectDesc = "div.row:contains(Description) p"
	override val selectState = "ul.manga-info li:contains(Status) a"
	override val selectAlt = "ul.manga-info li:contains(Other name (s))"
	override val selectTag = "ul.manga-info li:contains(Genre(s)) a"
	override val selectChapter = "tr"
	override val selectDate = "td i"
	override val selectPage = "img"
	override val selectBodyTag = "div.panel-body a"

	override fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("div.thumb-item-flow").map { div ->
			val href = "/" + div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirstOrThrow("div.img-in-ratio").attr("style").substringAfter("('")
					.substringBeforeLast("')"),
				title = div.selectFirstOrThrow("div.series-title").text().orEmpty(),
				altTitles = emptySet(),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				authors = emptySet(),
				state = null,
				source = source,
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
			)
		}
	}

	private val chapterListSelector = "div#list-chapters p, table.table tr, .list-chapters > a"

	private fun generateRandomStr(): String {
		return (1..30).map { toPathCharacters.random() }.joinToString("")
	}

	private val toPathCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

	override suspend fun getChapters(doc: Document): List<MangaChapter> {
		val slug = doc.selectFirstOrThrow("div.h0rating").attr("slug")
		val xhrUrl = "https://$domain/${generateRandomStr()}.lstc".toHttpUrl().newBuilder()
			.addQueryParameter("slug", slug)
			.build()
		val docLoad = webClient.httpGet(xhrUrl).parseHtml()

		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return docLoad.body().select(chapterListSelector).mapChapters(reversed = true) { i, a ->
			val href = "/" + a.selectFirstOrThrow("a.chapter").attrAsRelativeUrl("href")
			val dateText = a.selectFirst(selectDate)?.text()
			MangaChapter(
				id = generateUid(href),
				title = a.selectFirstOrThrow("a").text(),
				number = i + 1f,
				volume = 0,
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

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val cid = doc.selectFirstOrThrow("#chapter").attr("value")
		val dynamicPath = generateRandomStr()
		val imageUrlListUrl = "https://$domain/$dynamicPath.iog?cid=$cid"
		val headers = Headers.headersOf("Referer" , fullUrl)
		val docLoad = webClient.httpGet(imageUrlListUrl,  headers).parseHtml()

		val allImages = docLoad.select(selectPage)
		//remove page with ads
		val actualPages = allImages.filter { element ->
			element.attr("alt").startsWith("Page", ignoreCase = true)
		}

		return actualPages.map { img ->
			val url = img.requireSrc().toRelativeUrl(domain)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
