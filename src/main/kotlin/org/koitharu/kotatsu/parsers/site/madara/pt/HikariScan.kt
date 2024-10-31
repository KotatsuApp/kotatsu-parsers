package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HIKARISCAN", "HikariScan", "pt")
internal class HikariScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.HIKARISCAN, "hikariscan.org")
