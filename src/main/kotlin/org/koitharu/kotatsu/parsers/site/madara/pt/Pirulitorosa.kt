package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PIRULITOROSA", "Pirulitorosa", "pt", ContentType.HENTAI)
internal class Pirulitorosa(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.PIRULITOROSA, "pirulitorosa.site") {
	override val postreq = true
	override val datePattern: String = "dd/MM/yyyy"
}
