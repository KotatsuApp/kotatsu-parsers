package org.koitharu.kotatsu.parsers.site.madara.id


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.Locale

@MangaSourceParser("MANHWAPLUS", "Manhwa Plus", "id")
internal class ManhwaPlus(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWAPLUS, "manhwaplus.pro", 10) {

	override val isNsfwSource = true
	override val tagPrefix = "genre/"
	override val datePattern = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
	override val withoutAjax = true
	override val listeurl = "series/"

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
					append("/page/")
					append(pages.toString())
					append("/?s=")
					append(query.urlEncoded())
					append("&post_type=wp-manga&")
				}

				!tags.isNullOrEmpty() -> {
					append("/$tagPrefix")
					for (tag in tags) {
						append(tag.key)
					}
					append("/page/")
					append(pages.toString())
					append("?")
				}

				else -> {

					append("/$listeurl")
					append("/page/")
					append(pages.toString())
					append("?")
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
				coverUrl = div.selectFirst("img")?.attr("data-wpfc-original-src") ?: div.selectFirst("img")?.src()
					.orEmpty(),
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

}
