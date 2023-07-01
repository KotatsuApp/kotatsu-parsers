package org.koitharu.kotatsu.parsers.site.madara.fr


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("REAPERSCANS_FR", "ReaperScansFr", "fr")
internal class ReaperScansFr(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.REAPERSCANS_FR, "reaperscans.fr") {

	override val datePattern = "MM/dd/yyyy"

}
