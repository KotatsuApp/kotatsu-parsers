package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser

@MangaSourceParser("NETTRUYENMAX", "NettruyenBing", "vi")
internal class Nettruyenmax(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.NETTRUYENMAX, "www.nettruyenbb.com", 36)
