package org.koitharu.kotatsu.parsers.site.mangareader.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("HENTAISSSSSCANLATOR", "Sssscanlator Hentai", "pt", type = ContentType.HENTAI)
internal class HentaiSsssscanlator(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaSource.HENTAISSSSSCANLATOR,
		"hentais.sssscanlator.com",
		pageSize = 20,
		searchPageSize = 10,
	) {
	override val datePattern = "MMM d, yyyy"
}
