package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SCANTRADVF", "ScantradVf", "fr")
internal class ScantradVf(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SCANTRADVF, "scantrad-vf.co") {

	override val datePattern = "d MMMM yyyy"
	override val tagPrefix = "genre/"
}
