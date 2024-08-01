package org.koitharu.kotatsu.parsers.site.onemanga.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.onemanga.OneMangaParser

@MangaSourceParser("DANDADAN", "Dandadan", "fr")
internal class Dandadan(context: MangaLoaderContext) :
	OneMangaParser(context, MangaParserSource.DANDADAN, "dandadan.fr")
