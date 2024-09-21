package org.koitharu.kotatsu.parsers.site.scan.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.scan.ScanParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat

@MangaSourceParser("MANGAFR", "MangaFr", "fr")
internal class MangaFr(context: MangaLoaderContext) :
	ScanParser(context, MangaParserSource.MANGAFR, "www.mangafr.org") {
	override val listUrl = "/series"

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = emptySet(),
	)

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("MM-dd-yyyy", sourceLocale)
		return manga.copy(
			rating = doc.selectFirst(".card-series-detail .rate-value span, .card-series-about .rate-value span")
				?.ownText()?.toFloatOrNull()?.div(5f)
				?: RATING_UNKNOWN,
			tags = emptySet(),
			author = doc.selectFirst(".card-series-detail .col-6:contains(Autore) div, .card-series-about .mb-3:contains(Autore) a")
				?.text(),
			altTitle = doc.selectFirst(".card div.col-12.mb-4 h2, .card-series-about .h6")?.text().orEmpty(),
			description = doc.selectFirst(".card div.col-12.mb-4 p, .card-series-desc .mb-4 p")?.html().orEmpty(),
			chapters = doc.select(".chapters-list .col-chapter, .card-list-chapter .col-chapter")
				.mapChapters(reversed = true) { i, div ->
					val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
					MangaChapter(
						id = generateUid(href),
						name = div.selectFirstOrThrow("h5").html().substringBefore("<div").substringAfter("</span>"),
						number = i + 1f,
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = dateFormat.tryParse(doc.selectFirstOrThrow("h5 div").text()),
						branch = null,
						source = source,
					)
				},
		)
	}
}
