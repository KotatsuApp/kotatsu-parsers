package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RESETSCANS", "ResetScans", "en")
internal class ResetScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.RESETSCANS, "reset-scans.us", 18) {
	override val datePattern = "MMM dd"
}
