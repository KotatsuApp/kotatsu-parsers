package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("LEGACY_SCANS", "Legacy Scans", "fr")
internal class LegacyScansParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.LEGACY_SCANS, "legacy-scans.com", pageSize = 20, searchPageSize = 10)
