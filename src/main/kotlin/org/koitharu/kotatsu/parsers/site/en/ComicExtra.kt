package org.koitharu.kotatsu.parsers.site.en

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("COMICEXTRA", "ComicExtra", "en")
internal class ComicExtra(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.COMICEXTRA, 25) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY, SortOrder.UPDATED, SortOrder.NEWEST)

	override val configKeyDomain = ConfigKey.Domain("comicextra.net")

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_DESKTOP)
		.build()

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tag = tags.oneOrThrowIfMany()
		val url = buildString {
			append("https://$domain/")
			if (!tags.isNullOrEmpty()) {
				append(tag?.key.orEmpty())
				if (page > 1) {
					append(page)
				}
			} else if (!query.isNullOrEmpty()) {
				append("comic-search?key=")
				append(query.urlEncoded())
				if (page > 1) {
					append("&page=")
					append(page)
				}
			} else {
				when (sortOrder) {
					SortOrder.POPULARITY -> append("popular-comic/")
					SortOrder.UPDATED -> append("new-comic/")
					SortOrder.NEWEST -> append("recent-comic/")
					else -> append("new-comic/")
				}
				if (page > 1) {
					append(page)
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.movie-list-index div.cartoon-box").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.selectFirstOrThrow("h3").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = div.selectFirstOrThrow("img").attrAsAbsoluteUrl("src"),
				tags = emptySet(),
				state = when (div.selectFirstOrThrow(".detail:contains(Stasus: )").text()) {
					"Stasus: Ongoing" -> MangaState.ONGOING
					"Stasus: Completed" -> MangaState.FINISHED
					else -> null
				},
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/popular-comic").parseHtml()
		return doc.select("li.tag-item a").mapNotNullToSet { a ->
			MangaTag(
				key = a.attr("href").substringAfterLast('/'),
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("MM/dd/yy", sourceLocale)
		return manga.copy(
			altTitle = doc.selectFirstOrThrow("dt.movie-dt:contains(Alternate name:) + dd").text(),
			state = when (doc.selectFirstOrThrow("dt.movie-dt:contains(Status:) + dd a").text()) {
				"Ongoing" -> MangaState.ONGOING
				"Completed" -> MangaState.FINISHED
				else -> null
			},
			tags = doc.select("dt.movie-dt:contains(Genres:) + dd a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast("/"),
					title = a.text(),
					source = source,
				)
			},
			author = doc.select("dt.movie-dt:contains(Author:) + dd").text(),
			description = doc.getElementById("film-content")?.text(),
			chapters = doc.requireElementById("list").select("tr")
				.mapChapters(reversed = true) { i, tr ->
					val a = tr.selectFirstOrThrow("a")
					val url = a.attrAsRelativeUrl("href") + "/full"
					val name = a.text()
					val dateText = tr.select("td").last()?.text()
					MangaChapter(
						id = generateUid(url),
						name = name,
						number = i + 1,
						url = url,
						scanlator = null,
						uploadDate = dateFormat.tryParse(dateText),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		return doc.select(".chapter-container img.chapter_img").map { img ->
			val url = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
