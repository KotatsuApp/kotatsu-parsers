package org.koitharu.kotatsu.parsers.site.mangareader.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SSSSCANLATOR", "SssScanlator", "pt")
internal class SssScanlator(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaParserSource.SSSSCANLATOR,
		"sssscanlator.com.br",
		pageSize = 20,
		searchPageSize = 10,
	) {
	override val isTagsExclusionSupported = false
}
