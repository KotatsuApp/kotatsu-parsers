package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("EROMIAU", "Eromiau", "es")
internal class Eromiau(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.EROMIAU, "www.eromiau.com", 10) {

	override val isNsfwSource = true
	override val datePattern = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale("es")
}
