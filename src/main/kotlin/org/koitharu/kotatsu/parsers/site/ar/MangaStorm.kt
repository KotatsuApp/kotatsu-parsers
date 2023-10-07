package org.koitharu.kotatsu.parsers.site.ar

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MANGASTORM", "Manga Storm", "ar")
internal class MangaStorm(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.MANGASTORM, 30) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY)
	override val configKeyDomain = ConfigKey.Domain("mangastorm.org")

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

		val url =
			if (!tags.isNullOrEmpty()) {
				buildString {
					append("https://")
					append(domain)
					append("/categories/")
					append(tag?.key.orEmpty())
					append("?page=")
					append(page)
				}
			} else {
				buildString {
					append("https://")
					append(domain)
					append("/mangas?page=")
					append(page)
					if (!query.isNullOrEmpty()) {
						append("&query=")
						append(query.urlEncoded())
					}
				}
			}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.row div.col").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.select(".manga-ct-title").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = div.selectFirstOrThrow("img").src().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> = emptySet()

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()

		val root = doc.selectFirstOrThrow(".card-body .col-lg-9")

		return manga.copy(
			altTitle = null,
			state = null,
			tags = root.select(".flex-wrap a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('/'),
					title = a.text(),
					source = source,
				)
			},
			author = null,
			description = root.selectFirstOrThrow(".card-text").text(),
			chapters = doc.select(".card-body a.btn-fixed-width").mapChapters(reversed = true) { i, a ->
				val url = a.attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(url),
					name = a.text(),
					number = i + 1,
					url = url,
					scanlator = null,
					uploadDate = 0,
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml().requireElementById("content")
		return doc.select("div.text-center .img-fluid").map { img ->
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
