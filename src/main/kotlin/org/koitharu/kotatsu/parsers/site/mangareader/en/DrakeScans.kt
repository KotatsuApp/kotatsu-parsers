package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("DRAKESCANS", "DrakeScans", "en")
internal class DrakeScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.DRAKESCANS, "drakescans.com", 20, 10)
