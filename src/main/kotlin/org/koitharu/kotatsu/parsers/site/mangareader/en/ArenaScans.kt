package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ARENASCANS", "Team 11x11", "en")
internal class ArenaScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ARENASCANS, "team11x11.com", pageSize = 20, searchPageSize = 10)
