package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ROMANTIKMANGA", "RomantikManga", "tr")
internal class RomantikManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ROMANTIKMANGA, "romantikmanga.com", 20)
