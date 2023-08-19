package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWANEW", "Manhwa New", "en")
internal class ManhwaNew(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWANEW, "manhwanew.com") {
	override val datePattern = "MM/dd/yyyy"
}
