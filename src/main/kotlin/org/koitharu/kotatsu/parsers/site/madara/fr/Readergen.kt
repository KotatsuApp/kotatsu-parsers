package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("READERGEN", "ReaderGen", "fr")
internal class Readergen(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.READERGEN, "fr.readergen.fr", 18)
