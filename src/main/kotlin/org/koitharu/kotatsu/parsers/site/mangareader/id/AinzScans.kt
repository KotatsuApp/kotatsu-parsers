package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*


@MangaSourceParser("AINZSCANS", "Ainz Scans", "id")
internal class AinzScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.AINZSCANS, "ainzscans.site", pageSize = 20, searchPageSize = 10) {


	override val listUrl = "/series"
	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
}
