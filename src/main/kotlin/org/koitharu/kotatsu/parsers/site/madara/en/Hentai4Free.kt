package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("HENTAI_4FREE", "Hentai4Free", "en", ContentType.HENTAI)
internal class Hentai4Free(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.HENTAI_4FREE, "hentai4free.net", pageSize = 24) {

	override val tagPrefix = "hentai-tag/"
	override val listUrl = ""
	override val withoutAjax = true
	override val datePattern = "MMMM dd, yyyy"
	override val selectGenre = "div.tags-content a"

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/page/")
					append(page.toString())
					append("/?s=")
					append(filter.query.urlEncoded())
					append("&post_type=wp-manga")
				}

				else -> {

					val tag = filter.tags.oneOrThrowIfMany()
					if (filter.tags.isNotEmpty()) {
						append("/$tagPrefix")
						append(tag?.key.orEmpty())
						append("/")
						if (page > 1) {
							append("page/")
							append(page.toString())
						}
						append("/?")
					} else {
						if (page > 1) {
							append("/page/")
							append(page.toString())
						}

						append("/?s&post_type=wp-manga")

						filter.contentRating.oneOrThrowIfMany()?.let {
							append("&adult=")
							append(
								when (it) {
									ContentRating.SAFE -> "0"
									ContentRating.ADULT -> "1"
									else -> ""
								},
							)
						}

						filter.states.forEach {
							append("&status[]=")
							when (it) {
								MangaState.ONGOING -> append("on-going")
								MangaState.FINISHED -> append("end")
								MangaState.ABANDONED -> append("canceled")
								MangaState.PAUSED -> append("on-hold")
								MangaState.UPCOMING -> append("upcoming")
							}
						}

						append("&")
					}

					append("m_orderby=")
					when (order) {
						SortOrder.POPULARITY -> append("views")
						SortOrder.UPDATED -> append("latest")
						SortOrder.NEWEST -> append("new-manga")
						SortOrder.ALPHABETICAL -> append("alphabet")
						SortOrder.RATING -> append("rating")
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
				title = (summary?.selectFirst("h3") ?: summary?.selectFirst("h4")
				?: div.selectFirst(".manga-name"))?.text().orEmpty(),
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
}
