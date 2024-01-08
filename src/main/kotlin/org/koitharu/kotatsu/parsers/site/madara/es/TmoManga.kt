package org.koitharu.kotatsu.parsers.site.madara.es

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.EnumSet

@MangaSourceParser("TMOMANGA", "TmoManga", "es")
internal class TmoManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TMOMANGA, "tmomanga.com") {

	override val tagPrefix = "genero/"
	override val listUrl = "biblioteca/"
	override val selectGenre = "div.summary-content a.tags_manga"
	override val withoutAjax = true
	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY)
	override val availableStates: Set<MangaState> = emptySet()
	override val availableContentRating: Set<ContentRating> = emptySet()

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {
				is MangaListFilter.Search -> {
					append("/$listUrl")
					append("?search=")
					append(filter.query.urlEncoded())
					if (page > 1) {
						append("&page=")
						append(page)
					}
				}

				is MangaListFilter.Advanced -> {

					val tag = filter.tags.oneOrThrowIfMany()
					if (filter.tags.isNotEmpty()) {
						append("/$tagPrefix")
						append(tag?.key.orEmpty())
						if (page > 1) {
							append("?page=")
							append(page)
						}
					} else {
						append("/$listUrl")
						if (page > 1) {
							append("?page=")
							append(page)
						}
					}
				}

				null -> {
					append("/$listUrl")
					if (page > 1) {
						append("?page=")
						append(page)
					}
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.page-item-detail").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("h3").text(),
				altTitle = null,
				rating = div.selectFirst("span.total_votes")?.ownText()?.toFloatOrNull()?.div(5f) ?: -1f,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val link = href + stylePage
			val name = a.selectFirst("p")?.text() ?: a.ownText()
			MangaChapter(
				id = generateUid(href),
				name = name,
				number = i + 1,
				url = link,
				uploadDate = 0,
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().requireElementById("images_chapter")
		return root.select("img").map { img ->
			val url = img.src()?.toRelativeUrl(domain).orEmpty()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
