package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TEMPLESCANESP", "TempleScanEsp", "es" , ContentType.HENTAI)
internal class TempleScanEsp(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TEMPLESCANESP, "templescanesp.com") {

	override val listUrl =  "series/"
	override val tagPrefix = "genero/"
	override val datePattern = "dd.MM.yyyy"
}
