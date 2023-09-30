package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("POTATOMANGA", "Potato Manga", "ar")
internal class PotatoManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.POTATOMANGA, "potatomanga.xyz", pageSize = 30, searchPageSize = 10) {
	override val listUrl = "/series"
}
