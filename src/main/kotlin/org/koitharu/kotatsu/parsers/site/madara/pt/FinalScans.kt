package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale


@MangaSourceParser("FINALSCANS", "Final Scans", "pt")
internal class FinalScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.FINALSCANS, "finalscans.com") {

	override val datePattern: String = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale("pt", "PT")
	override val isNsfwSource = true
}
