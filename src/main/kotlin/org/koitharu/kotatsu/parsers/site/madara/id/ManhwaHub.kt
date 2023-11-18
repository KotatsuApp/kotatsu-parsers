package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MANHWAHUB", "ManhwaHub", "id", ContentType.HENTAI)
internal class ManhwaHub(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWAHUB, "manhwahub.net", 40) {

	override val tagPrefix = "genre/"
	override val datePattern = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
	override val withoutAjax = true
	override val listUrl = "genre/manhwa"
	override val selectTestAsync = "ul.box-list-chapter"

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

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
			when {
				!query.isNullOrEmpty() -> {
					append("/search?s=")
					append(query.urlEncoded())
					append("&page=")
					append(page)
					append("&")
				}

				!tags.isNullOrEmpty() -> {
					append("/$tagPrefix")
					append(tag?.key.orEmpty())
					append("?page=")
					append(page)
					append("&")
				}

				else -> {

					append("/$listUrl")
					append("?page=")
					append(page)
					append("&")
				}

			}

			append("m_orderby=")
			when (sortOrder) {
				SortOrder.POPULARITY -> append("views")
				SortOrder.UPDATED -> append("latest")
				SortOrder.NEWEST -> append("new-manga")
				SortOrder.ALPHABETICAL -> append("alphabet")
				SortOrder.RATING -> append("rating")
			}
		}
		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.row.c-tabs-item__content").ifEmpty {
			doc.select("div.page-item-detail")
		}.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			val summary = div.selectFirst(".tab-summary") ?: div.selectFirst(".item-summary")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = (summary?.selectFirst("h3") ?: summary?.selectFirst("h4")
				?: div.selectFirst("h5.series-title"))?.text().orEmpty(),
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
					?.lowercase()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain").parseHtml()
		return doc.select("div.genres li").mapNotNullToSet { li ->
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			val href = a.attr("href").removeSuffix("/").substringAfterLast(tagPrefix, "")
			MangaTag(
				key = href,
				title = a.ownText().trim().ifEmpty {
					a.selectFirst(".menu-image-title")?.text()?.trim() ?: return@mapNotNullToSet null
				}.toTitleCase(),
				source = source,
			)
		}
	}

}
