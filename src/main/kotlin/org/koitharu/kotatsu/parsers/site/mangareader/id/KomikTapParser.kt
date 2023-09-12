package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("KOMIKTAP", "KomikTap", "id")
internal class KomikTapParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKTAP, "92.87.6.124", pageSize = 25, searchPageSize = 10) {

	override val datePattern = "MMM d, yyyy"
}
