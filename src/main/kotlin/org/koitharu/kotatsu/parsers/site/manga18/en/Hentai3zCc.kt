package org.koitharu.kotatsu.parsers.site.manga18.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.manga18.Manga18Parser
import org.koitharu.kotatsu.parsers.util.*


@MangaSourceParser("HENTAI3ZCC", "Hentai3z Cc", "en", ContentType.HENTAI)
internal class Hentai3zCc(context: MangaLoaderContext) :
	Manga18Parser(context, MangaSource.HENTAI3ZCC, "hentai3z.cc") {

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			val pages = page + 1
			when {
				!query.isNullOrEmpty() -> {
					append("/$listeurl/")
					append(pages.toString())
					append("?search=")
					append(query.urlEncoded())
					append("&")
				}

				!tags.isNullOrEmpty() -> {
					append("/$tagUrl/")
					for (tag in tags) {
						append(tag.key)
					}
					append("/")
					append(pages.toString())
					append("?")
				}

				else -> {
					append("/$listeurl/")
					append(pages.toString())
					append("?")
				}
			}
			append("order_by=")
			when (sortOrder) {
				SortOrder.POPULARITY -> append("views")
				SortOrder.UPDATED -> append("lastest")
				SortOrder.ALPHABETICAL -> append("name")
				else -> append("latest")
			}
		}
		val doc = webClient.httpGet(url).parseHtml()


		return doc.select("div.story_item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src()
					?.replace("cover_thumb_2.webp", "cover_250x350.jpg")
					?.replace("admin.manga18.us", "bk.18porncomic.com")
					.orEmpty(),
				title = div.selectFirstOrThrow("div.mg_info").selectFirst("div.mg_name a")?.text().orEmpty(),
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
}
