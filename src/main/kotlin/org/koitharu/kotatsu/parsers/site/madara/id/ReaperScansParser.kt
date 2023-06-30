package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("REAPER_SCANS_ID", "ReaperScansID", "id")
internal class ReaperScansParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.REAPER_SCANS_ID, "reaperscans.id") {

	override val datePattern = "MMMM dd, yyyy"
	override val tagPrefix = "genre/"
	override val sourceLocale: Locale = Locale.ENGLISH

}
