package org.koitharu.kotatsu.parsers.site.madara.en

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.EnumSet

@MangaSourceParser("MANGAPURE", "MangaPure", "en")
internal class MangaPure(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAPURE, "mangapure.net") {
	override val tagPrefix = "mangas/"
	override val listUrl = "latest-manga/"
	override val datePattern = "MMMM d, HH:mm"

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
	)

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

					append("/?search=")
					append(query.urlEncoded())
					append("&page=")
					append(pages.toString())
					append("&post_type=wp-manga")
				}

				!tags.isNullOrEmpty() -> {
					append("/mangas/")
					append(tag?.key.orEmpty())
					append("?orderby=2&page=")
					append(pages.toString())

				}

				else -> {

					if (sortOrder == SortOrder.POPULARITY) {
						append("/popular-manga")
					}
					if (sortOrder == SortOrder.UPDATED) {
						append("/latest-manga")
					}
					append("?page=")
					append(pages.toString())
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.row.c-tabs-item__content").ifEmpty {
			doc.select("div.page-item-detail.manga")
		}.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			val summary = div.selectFirst(".tab-summary") ?: div.selectFirst(".item-summary")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = (summary?.selectFirst("h3") ?: summary?.selectFirst("h4"))?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirst("span.total_votes")?.ownText()?.toFloatOrNull()?.div(5f) ?: -1f,
				tags = summary?.selectFirst(".mg_genres")?.select("a")?.mapNotNullToSet { a ->
					MangaTag(
						key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
						title = a.text().ifEmpty { return@mapNotNullToSet null }.toTitleCase(),
						source = source,
					)
				}.orEmpty(),
				author = summary?.selectFirst(".mg_author")?.selectFirst("a")?.ownText(),
				state = when (summary?.selectFirst(".mg_status")?.selectFirst(".summary-content")?.ownText()?.trim()
					?.lowercase()) {
					"ongoing" -> MangaState.ONGOING
					"completed" -> MangaState.FINISHED
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun loadChapters(mangaUrl: String, document: Document): List<MangaChapter> {


		val mangaId = document.select("div[id^=manga-chapters-holder]").attr("data-id")

		val doc = webClient.httpGet("https://$domain/ajax-list-chapter?mangaID=$mangaId").parseHtml()

		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)

		return doc.select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val link = href + stylePage
			MangaChapter(
				id = generateUid(href),
				url = link,
				name = a.ownText(),
				number = i + 1,
				branch = null,
				uploadDate = parseChapterDate(
					dateFormat,
					li.selectFirst(selectDate)?.text(),
				),
				scanlator = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val urlarray = doc.select("p#arraydata").text().split(",").toTypedArray()
		return urlarray.map { url ->
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
