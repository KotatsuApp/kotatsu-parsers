package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("KOMIKGO", "KomikGo", "id", ContentType.HENTAI)
internal class KomikGo(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.KOMIKGO, "komikgo.xyz", pageSize = 20, searchPageSize = 10)
