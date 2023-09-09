package org.koitharu.kotatsu.parsers.site.foolslide.en


import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.foolslide.FoolSlideParser
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.host
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.requireElementById
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.ArrayList


@MangaSourceParser("ASSORTEDSCANS", "AssortedScans", "en")
internal class AssortedScans(context: MangaLoaderContext) :
	FoolSlideParser(context, MangaSource.ASSORTEDSCANS, "assortedscans.com", 56) {

	override val listUrl = "reader/"
	override val pagination = false
	override val selectInfo = "div.#series-info"

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {

		val doc = if (!query.isNullOrEmpty()) {

			val url = buildString {
				append("https://$domain/$searchUrl")
				append("?q=")
				append(query.urlEncoded())
				if (page > 1) {
					return emptyList()
				}
			}
			webClient.httpGet(url).parseHtml()
		} else {
			val url = buildString {
				append("https://$domain/$listUrl")
				// For some sites that don't have enough manga and page 2 links to page 1
				if (!pagination) {
					if (page > 1) {
						return emptyList()
					}
				} else {
					append(page.toString())
				}
			}
			webClient.httpGet(url).parseHtml()
		}

		return doc.select("section.series, tr.result").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),// in search no img
				title = div.selectFirstOrThrow("a").text().orEmpty(),
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

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val testAdultPage = webClient.httpGet(fullUrl).parseHtml()

		val doc = if (testAdultPage.selectFirst("div.info form") != null) {
			webClient.httpPost(fullUrl, "adult=true").parseHtml()
		} else {
			testAdultPage
		}
		val chapters = getChapters(manga, doc)

		val desc = doc.getElementById("series-desc")?.selectFirst("div")?.html()
		val alt = doc.getElementById("series-aliases")?.selectFirst("div.alias")?.text()
		val author = doc.getElementById("series-authors")?.selectFirst("div.author")?.text()
		val state = doc.getElementById("series-status")?.selectFirst("span")?.text()
		manga.copy(
			tags = emptySet(),
			coverUrl = doc.selectFirst(".cover")?.src().orEmpty(),// for manga result on search
			description = desc,
			altTitle = alt,
			author = author,
			state = when (state) {
				"Ongoing" -> MangaState.ONGOING
				"Completed" -> MangaState.FINISHED
				"Canceled" -> MangaState.ABANDONED
				else -> null
			},
			chapters = chapters,
		)
	}

	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		return doc.body().select("div.chapter").mapChapters(reversed = true) { i, div ->
			val a = div.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			MangaChapter(
				id = generateUid(href),
				name = a.text(),
				number = i + 1,
				url = href,
				uploadDate = 0,
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()
		val max = docs.selectFirstOrThrow(".curr-page input").attr("data-max").toInt() + 1
		val pages = ArrayList<MangaPage>(max)
		for (i in 1 until max) {
			val pagesUrl = chapterUrl + i
			val page = webClient.httpGet(pagesUrl).parseHtml().requireElementById("page-image").attr("src")
			pages.add(
				MangaPage(
					id = generateUid(page),
					url = page,
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}
}
