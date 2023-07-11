package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("IMPERIOSCANS", "Imperio Scans", "pt")
internal class ImperioScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.IMPERIOSCANS, "imperioscans.com.br") {

	override val datePattern: String = "dd/MM/yyyy"
}
