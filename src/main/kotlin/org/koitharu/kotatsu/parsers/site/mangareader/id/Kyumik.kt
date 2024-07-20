package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("KYUMIK", "Kyumik", "id", ContentType.HENTAI)
internal class Kyumik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.KYUMIK, "kyumik.com", pageSize = 20, searchPageSize = 10)
