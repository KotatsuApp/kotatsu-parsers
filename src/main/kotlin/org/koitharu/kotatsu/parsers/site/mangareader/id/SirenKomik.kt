package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SIRENKOMIK", "Siren Komik", "id")
internal class SirenKomik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SIRENKOMIK, "sirenkomik.my.id", pageSize = 20, searchPageSize = 10)
