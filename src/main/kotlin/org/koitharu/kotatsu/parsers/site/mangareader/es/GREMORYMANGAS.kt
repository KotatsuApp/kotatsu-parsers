package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("GREMORYMANGAS", "Gremory Mangas", "es")
internal class GREMORYMANGAS(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.GREMORYMANGAS, "gremorymangas.com", pageSize = 20, searchPageSize = 20)
