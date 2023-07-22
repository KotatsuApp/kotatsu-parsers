package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale


@MangaSourceParser("KOMIKLAB", "KomikLab", "id")
internal class KomikLabParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKLAB, "komiklab.com", pageSize = 20, searchPageSize = 10) {

	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
}
