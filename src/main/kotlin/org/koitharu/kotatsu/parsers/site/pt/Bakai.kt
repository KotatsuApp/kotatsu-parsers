package org.koitharu.kotatsu.parsers.site.pt

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("BAKAI", "Bakai", "pt", ContentType.HENTAI)
internal class Bakai(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.BAKAI, 15) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)
	override val configKeyDomain = ConfigKey.Domain("bakai.org")
	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_MOBILE)
		.build()

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			if (!query.isNullOrEmpty()) {
				append("/search/?q=")
				append(query.urlEncoded())
				append("&quick=1&type=cms_records1&page=")
				append(page.toString())
			} else if (!tags.isNullOrEmpty()) {
				append("/search/?tags=")
				for (tag in tags) {
					append(tag.key)
					append(",")
				}
				append("&quick=1&type=cms_records1&page=")
				append(page.toString())
			} else {
				append("/hentai/")
				append("page/")
				append(page.toString())
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		if (!tags.isNullOrEmpty() or !query.isNullOrEmpty()) {
			return doc.select("ol.ipsStream li.ipsStreamItem")
				.map { div ->
					val href = div.selectFirstOrThrow("div.ipsStreamItem_snippet a").attrAsRelativeUrl("href")
					Manga(
						id = generateUid(href),
						title = div.selectFirstOrThrow("h2.ipsStreamItem_title").text(),
						altTitle = null,
						url = href,
						publicUrl = href.toAbsoluteUrl(domain),
						rating = RATING_UNKNOWN,
						isNsfw = true,
						coverUrl = div.selectFirstOrThrow("span.ipsThumb img").attrAsAbsoluteUrl("src"),
						tags = setOf(),
						state = null,
						author = null,
						source = source,
					)
				}
		} else {
			return doc.select("section.ipsType_normal li.ipsGrid_span4")
				.map { div ->
					val href = div.selectFirstOrThrow("h2.ipsType_pageTitle a").attrAsRelativeUrl("href")
					Manga(
						id = generateUid(href),
						title = div.selectFirstOrThrow("h2.ipsType_pageTitle").text(),
						altTitle = null,
						url = href,
						publicUrl = href.toAbsoluteUrl(domain),
						rating = RATING_UNKNOWN,
						isNsfw = true,
						coverUrl = div.selectFirstOrThrow("img").attrAsAbsoluteUrl("src"),
						tags = setOf(),
						state = null,
						author = null,
						source = source,
					)
				}
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain").parseHtml()
		return doc.requireElementById("elNavigation_17_menu").select("li.ipsMenu_item a").mapNotNullToSet { a ->

			MangaTag(
				key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root =
			webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml().selectFirstOrThrow("article.ipsContained")
		return manga.copy(
			altTitle = null,
			state = null,
			tags = root.select("p:contains(Tags:) span span")[1].text().split(",").mapNotNullToSet { a ->
				val tag = a.replace(" ", "")
				MangaTag(
					key = tag,
					title = tag,
					source = source,
				)
			},
			author = root.selectFirstOrThrow("p:contains(Artista:) span a").text(),
			description = root.selectFirstOrThrow("section.ipsType_richText").html(),
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					name = manga.title,
					number = 1,
					url = manga.url,
					scanlator = null,
					uploadDate = 0,
					branch = null,
					source = source,
				),
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirstOrThrow("div.pular")
		return root.select("img").map { img ->
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
