package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.EnumSet

@Broken
@MangaSourceParser("MMSCANS", "MmScans", "en")
internal class MmScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MMSCANS, "mm-scans.org") {
	override val selectChapter = "li.chapter-li"
	override val selectDesc = "div.summary-text"
	override val withoutAjax = true
	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.NEWEST, SortOrder.ALPHABETICAL, SortOrder.RATING)
}
