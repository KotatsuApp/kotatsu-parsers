package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGARBIC", "Manga Arabic", "ar")
internal class Mangarbic(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGARBIC, "mangarabic.com") {
	override val postReq = true
	override val datePattern = "yyyy/MM/dd"
}
