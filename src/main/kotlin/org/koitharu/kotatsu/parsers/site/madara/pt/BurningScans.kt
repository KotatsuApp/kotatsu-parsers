package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("BURNINGSCANS", "BurningScans", "pt")
internal class BurningScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.BURNINGSCANS, "burningscans.com") {
	override val datePattern = "dd/MM/yyyy"
}
