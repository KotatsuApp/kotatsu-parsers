package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("READERGEN", "Readergen", "fr")
internal class Readergen(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.READERGEN, "fr.readergen.fr", 18)
