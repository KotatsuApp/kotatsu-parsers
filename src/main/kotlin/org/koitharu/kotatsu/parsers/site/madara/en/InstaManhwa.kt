package org.koitharu.kotatsu.parsers.site.madara.en

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.EnumSet

@MangaSourceParser("INSTAMANHWA", "InstaManhwa", "en", ContentType.HENTAI)
internal class InstaManhwa(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.INSTAMANHWA, "www.instamanhwa.com", 15) {
	override val tagPrefix = "genre/"
	override val listUrl = "latest/"
	override val postReq = true
	override val datePattern = "d MMMM, yyyy"

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
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
					append("&post_type=wp-manga&post_type=wp-manga")
				}

				!tags.isNullOrEmpty() -> {
					append("/genre/")
					append(tag?.key.orEmpty())
					append("?page=")
					append(pages.toString())

				}

				else -> {

					when (sortOrder) {
						SortOrder.UPDATED -> append("/latest")
						SortOrder.NEWEST -> append("/new")
						SortOrder.ALPHABETICAL -> append("/alphabet")
						else -> append("/latest")
					}
					append("?page=")
					append(pages.toString())
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.page-listing-item div.page-item-detail").ifEmpty {
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
					"completed " -> MangaState.FINISHED
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun loadChapters(mangaUrl: String, document: Document): List<MangaChapter> {

		val mangaId = document.select("div#manga-chapters-holder").attr("data-id")
		val token = document.select("meta")[2].attr("content")
		val url = "https://$domain/ajax"
		val postData = "_token=$token&action=manga_get_chapters&manga=$mangaId"
		val doc = webClient.httpPost(url, postData).parseHtml()

		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)

		return doc.select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val link = href + stylePage
			val dateText = li.selectFirst("a.c-new-tag")?.attr("title") ?: li.selectFirst(selectDate)?.text()
			val name = a.selectFirst("p")?.text() ?: a.ownText()
			MangaChapter(
				id = generateUid(href),
				url = link,
				name = name,
				number = i + 1,
				branch = null,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				scanlator = null,
				source = source,
			)
		}
	}
}
