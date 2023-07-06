package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("HERENSCAN", "Heren Scan", "es")
internal class HerenScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HERENSCAN, "herenscan.com") {

	override val datePattern = "d 'de' MMMMM 'de' yyyy"
	override val sourceLocale: Locale = Locale("es")
}
