package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGATX", "MangaTx", "en")
internal class MangaTx(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGATX, "mangatx.com") {
	override val datePattern = "MMMM dd, yyyy"
}
