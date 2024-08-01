package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("DRAGONTRANSLATION", "Dragon Translation", "es")
internal class DragonTranslationParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.DRAGONTRANSLATION, "dragontranslation.net", 30) {
	override val selectPage = "div#chapter_imgs img"
	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)
	override val availableStates: Set<MangaState> = emptySet()
	override val availableContentRating: Set<ContentRating> = emptySet()

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {
				is MangaListFilter.Search -> {
					append("/mangas?buscar=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				is MangaListFilter.Advanced -> {

					append("/mangas?page=")
					append(page.toString())

					val tag = filter.tags.oneOrThrowIfMany()
					if (filter.tags.isNotEmpty()) {
						append("&tag=")
						append(tag?.key.orEmpty())
					}
				}

				null -> {
					append("/mangas?page=")
					append(page.toString())
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
