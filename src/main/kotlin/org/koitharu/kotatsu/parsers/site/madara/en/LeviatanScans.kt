package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LEVIATANSCANS", "Leviatan Scans", "en")
internal class LeviatanScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LEVIATANSCANS, "en.leviatanscans.com", 10)
