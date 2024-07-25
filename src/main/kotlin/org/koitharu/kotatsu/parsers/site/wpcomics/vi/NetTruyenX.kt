package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser

@MangaSourceParser("NETTRUYENX", "NetTruyenX", "vi")
internal class NetTruyenX(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.NETTRUYENX, "nettruyenx.com")
