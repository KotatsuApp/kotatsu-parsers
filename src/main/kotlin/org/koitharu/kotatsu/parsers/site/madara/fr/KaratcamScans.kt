package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KARATCAMSCANS", "Karatcam Scans", "fr")
internal class KaratcamScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.KARATCAMSCANS, "karatcam-scans.fr") {

	override val tagPrefix = "webtoon-genre/"
	override val datePattern = "dd/MM/yyyy"
}
