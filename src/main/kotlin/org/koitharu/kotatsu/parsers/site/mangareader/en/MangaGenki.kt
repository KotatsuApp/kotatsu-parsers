package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGAGENKI", "Manga Genki", "en", ContentType.HENTAI)
internal class MangaGenki(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGAGENKI, "mangagenki.com", pageSize = 45, searchPageSize = 30)
