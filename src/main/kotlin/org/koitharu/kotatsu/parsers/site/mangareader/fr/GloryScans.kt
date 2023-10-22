package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("GLORYSCANS", "GloryScans", "fr")
internal class GloryScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.GLORYSCANS, "gloryscans.fr", pageSize = 20, searchPageSize = 10)
