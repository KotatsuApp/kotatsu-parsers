package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MELOKOMIK", "Melokomik", "id")
internal class Melokomik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MELOKOMIK, "melokomik.xyz", pageSize = 20, searchPageSize = 20) {

	override val datePattern = "MMM d, yyyy"

}
