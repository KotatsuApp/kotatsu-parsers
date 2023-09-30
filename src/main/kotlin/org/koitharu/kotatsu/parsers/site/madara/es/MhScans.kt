package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MHSCANS", "Mh Scans", "es")
internal class MhScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MHSCANS, "mhscans.com") {
	override val datePattern = "d 'de' MMMMM 'de' yyyy"
}
