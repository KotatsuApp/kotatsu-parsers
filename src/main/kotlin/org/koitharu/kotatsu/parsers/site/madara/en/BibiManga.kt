package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("BIBIMANGA", "BibiManga", "en")
internal class BibiManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.BIBIMANGA, "bibimanga.com") {

	override val isNsfwSource = false
	override val datePattern = "MMMM dd, yyyy"
}
