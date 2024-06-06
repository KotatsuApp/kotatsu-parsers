package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SINENSISSCANS", "SinensisScans", "pt")
internal class SinensisScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SINENSISSCANS, "sinensistoon.com") {
	override val datePattern: String = "dd/MM/yyyy"
}
