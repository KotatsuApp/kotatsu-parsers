package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser


@MangaSourceParser("KOMIKMAMA", "Komik Mama", "id")
internal class KomikMama(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKMAMA, "komikmama.co", pageSize = 20, searchPageSize = 20) {

	override val datePattern = "MMM d, yyyy"
}
