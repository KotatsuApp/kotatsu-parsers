package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("OMKOMIK", "OmKomik", "id")
internal class OmKomik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.OMKOMIK, "omkomik.com", pageSize = 20, searchPageSize = 10)
