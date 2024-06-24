package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ROCKSMANGA", "RocksManga", "ar")
internal class RocksManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ROCKSMANGA, "rocks-manga.com")
