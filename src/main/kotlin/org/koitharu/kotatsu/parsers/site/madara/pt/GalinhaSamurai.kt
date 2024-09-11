package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.EnumSet

@MangaSourceParser("GALINHASAMURAI", "GalinhaSamurai", "pt")
internal class GalinhaSamurai(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.GALINHASAMURAI, "galinhasamurai.com") {
	override val datePattern = "dd/MM/yyyy"
	override val withoutAjax = true
	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.NEWEST, SortOrder.ALPHABETICAL, SortOrder.RATING)
}
