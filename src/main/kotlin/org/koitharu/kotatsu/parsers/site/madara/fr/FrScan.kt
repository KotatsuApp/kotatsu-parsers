package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("FRSCAN", "FrScan", "fr")
internal class FrScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.FRSCAN, "fr-scan.com") {

	override val datePattern = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale.FRENCH

}
