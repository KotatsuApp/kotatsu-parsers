package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("DARK_SCANS", "DarkScans", "en")
internal class DarkScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.DARK_SCANS, "darkscans.com", 18)
