package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("EDOUJIN", "EDoujin", "en", ContentType.HENTAI)
internal class EDoujin(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.EDOUJIN, "edoujin.net", pageSize = 25, searchPageSize = 10)
