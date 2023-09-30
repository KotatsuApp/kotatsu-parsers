package org.koitharu.kotatsu.parsers.site.mangareader.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("FRANXXMANGAS", "Franxx Mangas", "pt", type = ContentType.HENTAI)
internal class FranxxMangas(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.FRANXXMANGAS, "franxxmangas.net", pageSize = 20, searchPageSize = 20) {
	override val datePattern = "MMM d, yyyy"
}
