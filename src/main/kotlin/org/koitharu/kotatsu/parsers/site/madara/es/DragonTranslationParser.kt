package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("DRAGONTRANSLATION", "DragonTranslation", "es")
internal class DragonTranslationParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.DRAGONTRANSLATION, "dragontranslation.net", 30) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
	)

	override val selectPage = "div#chapter_imgs img"

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
					append("/mangas?buscar=")
					append(query.urlEncoded())
					append("&page=")
					append(pages.toString())
				}

				!tags.isNullOrEmpty() -> {
					append("/mangas?tag=")
					for (tag in tags) {
						append(tag.key)
					}
					append("&page=")
					append(pages.toString())
				}

				else -> {

					append("/mangas")
					append("?page=")
					append(pages.toString())
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.video-bg div.col-6 ").map { div ->
			val href =
				div.selectFirst("a.series-link")?.attrAsRelativeUrlOrNull("href") ?: div.parseFailed("Link not found")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img.thumb-img")?.src().orEmpty(),
				title = div.selectFirst("div.series-box h5")?.text().orEmpty(),
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
}
