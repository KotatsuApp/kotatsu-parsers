package org.koitharu.kotatsu.parsers.site.foolslide.en

import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.foolslide.FoolSlideParser
import org.koitharu.kotatsu.parsers.util.*

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
			if (page > 1) {
				return emptyList()
			}
			val url = buildString {
				append("https://")
				append(domain)
				append('/')
				append(searchUrl)
				append("?q=")
				append(query.urlEncoded())
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
		val chapters = getChapters(doc)
		val desc = doc.getElementById("series-desc")?.selectFirst("div")?.html()
		val alt = doc.getElementById("series-aliases")?.selectFirst("div.alias")?.text()
		val author = doc.getElementById("series-authors")?.selectFirst("div.author")?.text()
		val state = doc.getElementById("series-status")?.selectFirst("span")?.text()
		manga.copy(
			coverUrl = doc.selectFirst(".cover")?.src() ?: manga.coverUrl,
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

	override suspend fun getChapters(doc: Document): List<MangaChapter> {
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
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.body().select(".page-list .dropdown-list li a").map { a ->
			val url = a.attr("href").toRelativeUrl(domain)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body()
		return root.requireElementById("page-image").attr("src") ?: doc.parseFailed("Page image not found")
	}
}
