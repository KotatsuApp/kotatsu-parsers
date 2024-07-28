package org.koitharu.kotatsu.parsers.site.onemanga.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.onemanga.OneMangaParser

@MangaSourceParser("HELLSPARADISESCAN", "HellsParadiseScan", "fr")
internal class HellsParadiseScan(context: MangaLoaderContext) :
	OneMangaParser(context, MangaParserSource.HELLSPARADISESCAN, "hellsparadisescan.com")
