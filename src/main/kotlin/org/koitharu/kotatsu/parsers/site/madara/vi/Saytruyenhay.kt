package org.koitharu.kotatsu.parsers.site.madara.vi

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*

@Broken
@MangaSourceParser("SAYTRUYENHAY", "PheTruyen", "vi")
internal class Saytruyenhay(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.SAYTRUYENHAY, "phetruyen.vip", 40) {

	override val tagPrefix = "genre/"
	override val withoutAjax = true
	override val listUrl = "public/genre/manga/"

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
					append("/search?s=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				else -> {

					val tag = filter.tags.oneOrThrowIfMany()
					if (filter.tags.isNotEmpty()) {
						append("/$tagPrefix")
						append(tag?.key.orEmpty())
					} else {
						append("/$listUrl")

					}
					append("?page=")
					append(page.toString())
					append("&m_orderby=")
					when (order) {
						SortOrder.UPDATED -> append("latest")
						SortOrder.RATING -> append("rating")
						SortOrder.POPULARITY -> append("views")
						SortOrder.NEWEST -> append("new")
						else -> append("latest")
					}
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()


		return doc.select("div.row.c-tabs-item__content").ifEmpty {
			doc.select("div.page-item-detail")
		}.map { div ->
			val href = div.selectFirst("a")?.attrAsRelativeUrlOrNull("href") ?: div.parseFailed("Link not found")
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
				state = when (summary?.selectFirst(".mg_status")?.selectFirst(".summary-content")?.ownText()
					?.lowercase().orEmpty()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val body = doc.body()

		val chaptersDeferred = async { getChapters(manga, doc) }

		val desc = body.select(selectDesc).let {
			if (it.select("p").text().isNotEmpty()) {
				it.select("p").joinToString(separator = "\n\n") { p ->
					p.text().replace("<br>", "\n")
				}
			} else {
				it.text()
			}
		}

		val stateDiv = (body.selectFirst("div.post-content_item:contains(Status)")
			?: body.selectFirst("div.post-content_item:contains(Statut)")
			?: body.selectFirst("div.post-content_item:contains(État)")
			?: body.selectFirst("div.post-content_item:contains(حالة العمل)")
			?: body.selectFirst("div.post-content_item:contains(Estado)")
			?: body.selectFirst("div.post-content_item:contains(สถานะ)")
			?: body.selectFirst("div.post-content_item:contains(Stato)")
			?: body.selectFirst("div.post-content_item:contains(Durum)")
			?: body.selectFirst("div.post-content_item:contains(Statüsü)")
			?: body.selectFirst("div.post-content_item:contains(Статус)")
			?: body.selectFirst("div.post-content_item:contains(状态)")
			?: body.selectFirst("div.post-content_item:contains(الحالة)"))?.selectLast("div.summary-content")

		val state = stateDiv?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}

		val alt =
			doc.body().select(".post-content_item:contains(Alt) .summary-content").firstOrNull()?.tableValue()
				?.textOrNull()
				?: doc.body().select(".post-content_item:contains(Nomes alternativos: ) .summary-content")
					.firstOrNull()?.tableValue()?.textOrNull()

		manga.copy(
			tags = doc.body().select(selectGenre).mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}

}
