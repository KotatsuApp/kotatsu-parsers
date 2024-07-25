package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser

@MangaSourceParser("NETTRUYENSS", "NhatTruyenSS", "vi")
internal class NhatTruyenSS(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.NETTRUYENSS, "www.nhattruyenss.net")
