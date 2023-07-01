package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("MANGAKEYFI", "Mangakeyfi", "tr")
internal class Mangakeyfi(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAKEYFI, "mangakeyfi.net", 20) {
	override val tagPrefix = "mangalar-genre/"

	override val datePattern = "d MMMM yyyy"
	override val sourceLocale: Locale = Locale("tr")

}
