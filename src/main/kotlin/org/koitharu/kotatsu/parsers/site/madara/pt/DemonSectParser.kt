package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("demonsect", "DemonSect", "pt")
internal class DemonSectParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.demonsect, "demonsect.com.br", 10) {
	override val datePattern = "MMM dd, yyyy"
}
