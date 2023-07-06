package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("ATLANTISSCAN", "Atlantisscan", "pt")
internal class Atlantisscan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ATLANTISSCAN, "br.atlantisscan.com", pageSize = 50) {

	override val datePattern = "dd/MM/yyyy"

}
