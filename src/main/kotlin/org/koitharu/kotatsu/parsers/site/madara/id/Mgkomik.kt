package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.*

@MangaSourceParser("MGKOMIK", "Mgkomik", "id")
internal class Mgkomik(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MGKOMIK, "mgkomik.com", 20) {

	override val tagPrefix = "genres/"
	override val datePattern = "dd MMM yy"
	override val sourceLocale: Locale = Locale.ENGLISH
}
