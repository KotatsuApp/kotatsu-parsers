package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RESETSCANS", "Reset Scans", "en")
internal class ResetScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.RESETSCANS, "reset-scans.com", 18) {
	override val datePattern = "MMM dd"
}
