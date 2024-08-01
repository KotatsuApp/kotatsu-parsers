package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SENPAIEDICIONES", "SenpaiEdiciones", "es")
internal class Senpaiediciones(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaParserSource.SENPAIEDICIONES,
		"senpaiediciones.com",
		pageSize = 20,
		searchPageSize = 20,
	) {
	override val datePattern = "MMM d, yyyy"
}
