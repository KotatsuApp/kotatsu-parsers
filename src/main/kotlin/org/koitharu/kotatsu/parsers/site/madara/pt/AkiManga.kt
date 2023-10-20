package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("AKIMANGA", "AkiManga", "pt")
internal class AkiManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.AKIMANGA, "akimanga.com") {
	override val datePattern = "dd/MM/yyyy"
}
