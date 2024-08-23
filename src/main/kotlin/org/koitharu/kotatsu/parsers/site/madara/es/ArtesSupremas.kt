package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ARTESSUPREMAS", "ArtesSupremas", "es")
internal class ArtesSupremas(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ARTESSUPREMAS, "artessupremas.com") {
	override val datePattern = "dd/MM/yyyy"
}
