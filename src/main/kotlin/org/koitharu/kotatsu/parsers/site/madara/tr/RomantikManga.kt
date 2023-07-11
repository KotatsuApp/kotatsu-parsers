package org.koitharu.kotatsu.parsers.site.madara.tr


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("ROMANTIKMANGA", "Romantik Manga", "tr")
internal class RomantikManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ROMANTIKMANGA, "romantikmanga.com", 20) {

	override val datePattern = "MMMM d, yyyy"
}
