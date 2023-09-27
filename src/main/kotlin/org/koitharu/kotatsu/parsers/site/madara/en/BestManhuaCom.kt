package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("BESTMANHUACOM", "Best Manhua .Com", "en")
internal class BestManhuaCom(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.BESTMANHUACOM, "bestmanhua.com", 20) {
	override val datePattern = "dd MMMM yyyy"
	override val tagPrefix = "genres/"
	override val listUrl = "all-manga/"
	override val withoutAjax = true
	override val selectDesc = "div.dsct"
	override val selectTestAsync = "div.panel-manga-chapter"
	override val selectDate = "span.chapter-time"
	override val selectChapter = "li.a-h"
	override val selectBodyPage = "div.manga-content div.read-content"
	override val selectPage = "div.image-placeholder"

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tag = tags.oneOrThrowIfMany()
		val url = buildString {
			append("https://")
			append(domain)
			val pages = page + 1
			when {
				!query.isNullOrEmpty() -> {
					append("/page/")
					append(pages.toString())
					append("/?s=")
					append(query.urlEncoded())
					append("&post_type=wp-manga&")
				}

				!tags.isNullOrEmpty() -> {
					append("/$tagPrefix")
					append(tag?.key.orEmpty())
					append("/")
					append(pages.toString())
					append("?")
				}

				else -> {
					append("/$listUrl")
					append(pages.toString())
					append("?")
				}
			}
			append("sort=")
			when (sortOrder) {
				SortOrder.POPULARITY -> append("most-viewd")
				SortOrder.UPDATED -> append("latest-updated")
				SortOrder.NEWEST -> append("release-date")
				SortOrder.ALPHABETICAL -> append("name-az")
				SortOrder.RATING -> append("rating")
			}
		}
		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.page-item").map { div ->
			val href = div.selectFirst("a")?.attrAsRelativeUrlOrNull("href") ?: div.parseFailed("Link not found")
			val summary = div.selectFirstOrThrow(".bigor-manga")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = summary.selectFirst("h3")?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirstOrThrow("div.item-rate span").ownText().toFloatOrNull()?.div(5f) ?: -1f,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val chapterId =
			doc.selectFirst("script:containsData(chapter_id = )")?.toString()?.substringAfter("chapter_id = ")
				?.substringBefore(",")

		val json =
			webClient.httpGet("https://$domain/ajax/image/list/chap/$chapterId?mode=vertical&quality=high").parseJson()

		val html = json.getString("html").split("/div>")

		val pages = ArrayList<MangaPage>()

		html.map { t ->
			if (t.contains("data-src=")) {
				val url = t.substringAfter("data-src=\"").substringBefore("\"")
				pages.add(
					MangaPage(
						id = generateUid(url),
						url = url,
						preview = null,
						source = source,
					),
				)
			}
		}
		return pages
	}
}
