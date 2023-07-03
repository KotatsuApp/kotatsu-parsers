package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale


@MangaSourceParser("FOXWHITE", "Fox White", "pt")
internal class FoxWhite(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.FOXWHITE, "foxwhite.com.br") {

	override val datePattern = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale("pt", "PT")
}
