package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("THEGUILDSCANS", "Theguildscans", "en")
internal class Theguildscans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.THEGUILDSCANS, "theguildscans.com") {

	override val postreq = true
}
