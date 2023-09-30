package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RAIJINSCANS", "Raijin Scans", "fr")
internal class RaijinScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.RAIJINSCANS, "raijinscans.fr") {
	override val datePattern = "dd/MM/yyyy"
}
