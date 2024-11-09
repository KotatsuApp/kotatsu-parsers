package org.koitharu.kotatsu.parsers.site.zeistmanga.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow

@MangaSourceParser("MIKROKOSMOSFB", "Mikrokosmosfb", "tr", ContentType.HENTAI)
internal class Mikrokosmosfb(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.MIKROKOSMOSFB, "mikrokosmosfb.blogspot.com") {
	override val sateOngoing: String = "Devam Ediyor"
	override val sateFinished: String = "Tamamlandı"
	override val sateAbandoned: String = "Güncel"

	override suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain").parseHtml()
		val tags = doc.selectFirstOrThrow("script:containsData(label: )").data()
			.substringAfter("label: [").substringBefore("]").replace("\"", "").split(", ")
		return tags.mapToSet {
			MangaTag(
				key = it,
				title = it,
				source = source,
			)
		}
	}
}
