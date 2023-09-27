package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGAFLAME", "Manga Flame", "ar")
internal class MangaFlame(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGAFLAME, "mangaflame.org", pageSize = 20, searchPageSize = 10)
