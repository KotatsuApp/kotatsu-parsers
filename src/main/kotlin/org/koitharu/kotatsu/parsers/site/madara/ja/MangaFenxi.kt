package org.koitharu.kotatsu.parsers.site.madara.ja

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrlOrNull
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.host
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseFailed
import org.koitharu.kotatsu.parsers.util.removeSuffix
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import java.util.Locale

@MangaSourceParser("MANGAFENXI", "MangaFenxi", "ja")
internal class MangaFenxi(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGAFENXI, "mangafenxi.net", 40) {
	override val sourceLocale: Locale = Locale.ENGLISH
	override val tagPrefix = "genres/"

	override fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("div.row.c-tabs-item__content").ifEmpty {
			doc.select("div.page-item-detail")
		}.map { div ->
			val href = div.selectFirst("a")?.attrAsRelativeUrlOrNull("href") ?: div.parseFailed("Link not found")
			val summary = div.selectFirst(".tab-summary") ?: div.selectFirst(".item-summary")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.attr("src")?.replace("-193x278", "").orEmpty(),
				title = (summary?.selectFirst("h3") ?: summary?.selectFirst("h4")
				?: div.selectFirst(".manga-name") ?: div.selectFirst(".post-title"))?.text().orEmpty(),
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
					in upcoming -> MangaState.UPCOMING
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}
}
