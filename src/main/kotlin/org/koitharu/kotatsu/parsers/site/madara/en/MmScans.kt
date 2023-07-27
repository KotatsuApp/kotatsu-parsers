package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("MMSCANS", "Mm Scans", "en")
internal class MmScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MMSCANS, "mm-scans.org") {

	override val selectchapter = "li.chapter-li"
	override val selectdesc = "div.summary-text"
	override val withoutAjax = true
}
