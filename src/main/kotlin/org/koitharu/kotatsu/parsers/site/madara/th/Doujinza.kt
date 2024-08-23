package org.koitharu.kotatsu.parsers.site.madara.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.EnumSet

@MangaSourceParser("DOUJINZA", "Doujinza", "th", ContentType.HENTAI)
internal class Doujinza(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.DOUJINZA, "doujinza.com", 24) {
	override val withoutAjax = true
	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.NEWEST, SortOrder.ALPHABETICAL, SortOrder.RATING)
	override val datePattern = "MMMM dd, yyyy"
	override val listUrl = "doujin/"
	override val tagPrefix = "doujin-genre/"
}
