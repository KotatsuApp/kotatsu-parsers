package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MIRRORDESU", "Mirrordesu", "id", ContentType.HENTAI)
internal class Mirrordesu(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MIRRORDESU, "mirrordesu.ink", pageSize = 20, searchPageSize = 20) {

	override val listUrl = "/komik"
	override val datePattern = "MMM d, yyyy"

}
