package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@Broken
@MangaSourceParser("PANTHEONSCAN_FR", "PantheonScan.fr", "fr")
internal class PantheonScanFr(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaParserSource.PANTHEONSCAN_FR,
		"www.pantheon-scan.fr",
		pageSize = 40,
		searchPageSize = 10,
	)
