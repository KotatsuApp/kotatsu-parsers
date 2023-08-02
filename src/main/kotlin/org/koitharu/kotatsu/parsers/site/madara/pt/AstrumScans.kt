package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ASTRUMSCANS", "Astrum Scans", "pt")
internal class AstrumScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ASTRUMSCANS, "astrumscans.xyz", 20) {

	override val withoutAjax = true
	override val listUrl = "series/"
	override val datePattern = "dd/MM/yyyy"
}
