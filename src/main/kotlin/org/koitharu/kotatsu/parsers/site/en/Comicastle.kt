package org.koitharu.kotatsu.parsers.site.en

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("COMICASTLE", "Comicastle", "en")
internal class Comicastle(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.COMICASTLE, 42) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY, SortOrder.UPDATED)

	override val configKeyDomain = ConfigKey.Domain("comicastle.org")

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_DESKTOP)
		.build()

	init {
		paginator.firstPage = 0
		searchPaginator.firstPage = 0
	}

	override suspend fun getFavicons(): Favicons {
		return Favicons(
			listOf(
				Favicon("https://$domain/assets/static/app-assets/images/logo/logo-primary.png", 54, null),
			),
			domain,
		)
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tag = tags.oneOrThrowIfMany()
		val doc = if (!query.isNullOrEmpty()) {
			val url = buildString {
				append("https://$domain/library/search/result/")
				append(page + 1)
			}
			val postdata = "submit=Submit&search=" + query.urlEncoded()
			webClient.httpPost(url, postdata).parseHtml()

		} else if (!tags.isNullOrEmpty()) {
			val url = buildString {
				append("https://$domain/library/search/genre/")
				append(page + 1)
			}
			val postdata = "submit=Submit&search=" + tag?.key.orEmpty()
			webClient.httpPost(url, postdata).parseHtml()

		} else {
			val url = buildString {
				append("https://$domain")
				append("/library/")
				when (sortOrder) {
					SortOrder.POPULARITY -> append("popular/desc/")
					SortOrder.UPDATED -> append("postdate/desc")
					else -> append("postdate/desc")
				}
				append("/index/")
				append(page * pageSize)
			}
			webClient.httpGet(url).parseHtml()
		}

		return doc.select("div.card-body div.match-height div.col-6")
			.map { div ->
				val href = div.selectFirstOrThrow("a").attrAsAbsoluteUrl("href")
				Manga(
					id = generateUid(href),
					title = div.selectFirstOrThrow("p").text(),
					altTitle = null,
					url = href,
					publicUrl = href.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					isNsfw = false,
					coverUrl = div.selectFirstOrThrow("img").attrAsAbsoluteUrl("data-src"),
					tags = emptySet(),
					state = null,
					author = null,
					source = source,
				)
			}
	}


	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/library/").parseHtml()
		return doc.requireElementById("sidebar").selectFirstOrThrow(".card-body").select("button")
			.mapNotNullToSet { button ->
				MangaTag(
					key = button.attr("value"),
					title = button.text(),
					source = source,
				)
			}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		return manga.copy(
			altTitle = null,
			state = when (root.selectFirstOrThrow(".card-body p span.mr-1 strong").text()) {
				"Ongoing" -> MangaState.ONGOING
				"Completed" -> MangaState.FINISHED
				else -> null
			},
			tags = root.select("p:contains(Genre) ~ div form").mapNotNullToSet { form ->
				MangaTag(
					key = form.selectFirstOrThrow("input").attr("value"),
					title = form.selectFirstOrThrow("button").text(),
					source = source,
				)
			},
			author = root.select("thead:contains(Writer) + tbody button").text(),
			description = root.getElementById("comic-desc")?.text(),
			chapters = root.select("div.card-body > .table-responsive tr a")
				.mapChapters { i, a ->
					val url = a.attrAsRelativeUrl("href")
					val name = a.text()
					MangaChapter(
						id = generateUid(url),
						name = name,
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
		val doc = webClient.httpGet(fullUrl).parseHtml()

		return doc.selectFirstOrThrow(".card-content .form-control.pr-3").select("option").map { option ->
			val url = option.attr("alt")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

}
