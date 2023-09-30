package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@MangaSourceParser("SOULSCANS", "Soul Scans", "id")
internal class SoulScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SOULSCANS, "soulscans.my.id", pageSize = 30, searchPageSize = 30) {
	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
}
