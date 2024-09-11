package org.koitharu.kotatsu.parsers.site.madara.en

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("FIRESCANS", "FireScans", "en")
internal class FireScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.FIRESCANS, "firescans.xyz", 10) {

	override fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("div.row.c-tabs-item__content").ifEmpty {
			doc.select("div.page-item-detail")
		}.map { div ->
			val href = div.selectFirst("h3 a")?.attrAsRelativeUrlOrNull("href") ?: div.parseFailed("Link not found")
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
				state = when (
					summary?.selectFirst(".mg_status")
						?.selectFirst(".summary-content")
						?.ownText()
						.orEmpty()
				) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					in abandoned -> MangaState.ABANDONED
					in paused -> MangaState.PAUSED
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/?s=&post_type=wp-manga").parseHtml()
		return doc.select("form.search-advanced-form div.form-group div.checkbox ").mapNotNullToSet { div ->
			val key = div.selectFirst("input")?.attr("value") ?: return@mapNotNullToSet null
			MangaTag(
				key = key,
				title = div.selectFirst("label")?.text() ?: key,
				source = source,
			)
		}
	}
}
