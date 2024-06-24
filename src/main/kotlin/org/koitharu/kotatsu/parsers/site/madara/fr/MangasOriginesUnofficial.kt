package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGASORIGINESUNOFFICIAL", "MangasOrigines.xyz", "fr")
internal class MangasOriginesUnofficial(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGASORIGINESUNOFFICIAL, "mangas-origines.xyz")
