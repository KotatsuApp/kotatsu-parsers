package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAROCKY", "Manga Rocky", "en")
internal class MangaRocky(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAROCKY, "mangarocky.com") {

	override val postreq = true

}
