package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RAGNAROKSCANLATION", "Ragnarok Scanlation", "es")
internal class RagnarokScanlation(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.RAGNAROKSCANLATION, "ragnarokscanlation.com") {

	override val datePattern = "MMMM d, yyyy"
}
