package org.koitharu.kotatsu.parsers.site.zeistmanga.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("TYRANTSCANS", "TyrantScans", "pt")
internal class TyrantScans(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.TYRANTSCANS, "www.tyrantscans.com")
