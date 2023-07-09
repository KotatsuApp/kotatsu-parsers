package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RANDOMSCANS", "Random Scans", "pt")
internal class RandomScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.RANDOMSCANS, "randomscans.com") {

	override val datePattern: String = "MMMM d, yyyy"
}
