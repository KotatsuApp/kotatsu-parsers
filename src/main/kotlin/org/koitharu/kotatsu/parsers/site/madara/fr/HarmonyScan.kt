package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HARMONYSCAN", "HarmonyScan", "fr")
internal class HarmonyScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.HARMONYSCAN, "harmony-scan.fr")
