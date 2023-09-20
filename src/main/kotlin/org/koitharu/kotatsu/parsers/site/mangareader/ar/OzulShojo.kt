package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("OZULSHOJO", "OzulShojo", "ar")
internal class OzulShojo(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.OZULSHOJO, "ozulshojo.com", pageSize = 20, searchPageSize = 10)
