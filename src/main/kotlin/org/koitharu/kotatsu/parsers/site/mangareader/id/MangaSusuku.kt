package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale

@MangaSourceParser("MANGASUSUKU", "MangaSusuku", "id")
internal class MangaSusuku(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGASUSUKU, "mangasusuku.xyz", pageSize = 20, searchPageSize = 20) {

	override val listUrl = "/komik"
	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
	override val isNsfwSource = true
}
