package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("IZANAMISCANS", "IzanamiScans", "id")
internal class IzanamiScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.IZANAMISCANS, "izanamiscans.my.id", pageSize = 20, searchPageSize = 10)
