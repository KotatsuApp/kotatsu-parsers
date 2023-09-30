package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SCAMBERTRASLATOR", "Scamber Traslator", "es")
internal class Scambertraslator(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SCAMBERTRASLATOR, "scambertraslator.com") {
	override val datePattern = "dd/MM/yyyy"
}
