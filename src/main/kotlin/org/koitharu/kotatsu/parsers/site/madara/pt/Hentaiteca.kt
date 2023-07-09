package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HENTAITECA", "Hentaiteca", "pt")
internal class Hentaiteca(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HENTAITECA, "hentaiteca.net", pageSize = 10) {

	override val datePattern = "MM/dd/yyyy"
	override val tagPrefix = "genero/"
	override val isNsfwSource = true
}
