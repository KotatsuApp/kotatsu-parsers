package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@Broken // no longer works with MadaraParser, need fix
@MangaSourceParser("DRAGONTRANSLATION", "Dragon Translation", "es")
internal class DragonTranslationParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.DRAGONTRANSLATION, "dragontranslation.net", 30) {

	override val selectPage = "div#chapter_imgs img"
	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override suspend fun getFilterOptions() = super.getFilterOptions().copy(
		availableStates = emptySet(),
		availableContentRating = emptySet(),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/mangas?buscar=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				else -> {

					append("/mangas?page=")
					append(page.toString())

					val tag = filter.tags.oneOrThrowIfMany()
					if (filter.tags.isNotEmpty()) {
						append("&tag=")
						append(tag?.key.orEmpty())
					}
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		val row = doc.select("div.row.gy-3").firstOrNull() ?: return emptyList()
			return row.select("article.position-relative.card").mapNotNull { div ->
				val href = div.selectFirst("a.lanzador")?.attrAsRelativeUrlOrNull("href") ?: return@mapNotNull null
				val coverUrl = div.selectFirst("img.card-img-top.wp-post-image.lazy.loaded")?.src().orEmpty()
				Manga(
					id = generateUid(href),
					url = href,
					publicUrl = href,
					coverUrl = coverUrl,
					title = div.selectFirst("h2.card-title.fs-6.entry-title").text(),
					altTitles = emptySet(),
					rating = RATING_UNKNOWN,
					tags = emptySet(),
					authors = emptySet(),
					state = null,
					source = source,
					contentRating = if (isNsfwSource) ContentRating.ADULT else null,
				)
			}
		}
	}
