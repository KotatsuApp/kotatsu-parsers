package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("IMMORTALUPDATES", "Immortal Updates", "en")
internal class ImmortalUpdates(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.IMMORTALUPDATES, "immortalupdates.com") {
	override val listUrl = "mangas/"
}
