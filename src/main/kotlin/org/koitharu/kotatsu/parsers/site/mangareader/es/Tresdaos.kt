package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("TRESDAOS", "Tresdaos", "es")
internal class Tresdaos(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.TRESDAOS, "tresdaos.com", 20, 10)
