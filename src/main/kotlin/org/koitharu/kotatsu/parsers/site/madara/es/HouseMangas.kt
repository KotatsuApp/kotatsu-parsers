package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("HOUSEMANGAS", "HouseMangas", "es")
internal class HouseMangas(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.HOUSEMANGAS, "housemangas.com")
