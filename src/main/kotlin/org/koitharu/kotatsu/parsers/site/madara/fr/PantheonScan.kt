package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PANTHEONSCAN", "Pantheon Scan", "fr")
internal class PantheonScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.PANTHEONSCAN, "pantheon-scan.com") {
	override val datePattern = "d MMMM yyyy"
}
