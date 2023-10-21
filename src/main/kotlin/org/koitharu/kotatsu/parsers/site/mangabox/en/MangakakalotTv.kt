package org.koitharu.kotatsu.parsers.site.mangabox.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.mangabox.MangaboxParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("MANGAKAKALOTTV", "Mangakakalot.tv", "en")
internal class MangakakalotTv(context: MangaLoaderContext) :
	MangaboxParser(context, MangaSource.MANGAKAKALOTTV) {

	override val configKeyDomain = ConfigKey.Domain("ww6.mangakakalot.tv")
	override val searchUrl = "/search/"
	override val listUrl = "/manga_list"

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
			if (!query.isNullOrEmpty()) {
				append(searchUrl)
				append(query.urlEncoded())
				append("?page=")
				append(page.toString())
			} else {
				append("$listUrl/")
				append("?type=")
				when (sortOrder) {
					SortOrder.POPULARITY -> append("topview")
					SortOrder.UPDATED -> append("latest")
					SortOrder.NEWEST -> append("newest")
					else -> append("latest")
				}
				if (!tags.isNullOrEmpty()) {
					append("&category=")
					append(tag?.key.orEmpty())
				}
				if (page > 1) {
					append("&page=")
					append(page.toString())
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.list-truyen-item-wrap").ifEmpty {
			doc.select("div.story_item")
		}.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("h3").text().orEmpty(),
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

	override val selectTagMap = "ul.tag li a"

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		return doc.select(selectTagMap).mapNotNullToSet { a ->
			MangaTag(
				key = a.attr("href").substringAfterLast("category=").substringBefore("&"),
				title = a.attr("title"),
				source = source,
			)
		}
	}
}
