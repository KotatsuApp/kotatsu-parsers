package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("NEOX_SCANS", "Neox scans", "pt")
internal class Neoxscans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.NEOX_SCANS, "neoxscan.net", 18) {
	override val datePattern = "dd/MM/yyyy"
}
