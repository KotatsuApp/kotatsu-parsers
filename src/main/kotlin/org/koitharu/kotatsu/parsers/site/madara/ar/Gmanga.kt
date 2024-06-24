package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GMANGA", "Gmanga", "ar")
internal class Gmanga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.GMANGA, "gmanga.site")
