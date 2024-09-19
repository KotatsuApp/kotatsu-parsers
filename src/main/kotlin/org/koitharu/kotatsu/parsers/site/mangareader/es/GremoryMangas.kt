package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("GREMORYMANGAS", "GremoryMangas", "es")
internal class GremoryMangas(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaParserSource.GREMORYMANGAS,
		"gremorymangas.com",
		pageSize = 20,
		searchPageSize = 20,
	) {
	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
		)
}
