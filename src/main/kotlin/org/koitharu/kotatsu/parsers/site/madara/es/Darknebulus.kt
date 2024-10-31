package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("DARKNEBULUS", "Darknebulus", "es")
internal class Darknebulus(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.DARKNEBULUS, "www.darknebulus.com")
