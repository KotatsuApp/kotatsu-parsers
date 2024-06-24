package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser

@MangaSourceParser("NHATTRUYENMIN", "NhattruyenPlus", "vi")
internal class Nhattruyenmin(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.NHATTRUYENMIN, "nhattruyenmax.com")
