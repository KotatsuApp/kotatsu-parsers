package org.koitharu.kotatsu.parsers.site.zeistmanga.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("NEKOSCANS", "NekoScans", "es")
internal class NekoScans(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.NEKOSCANS, "www.nekoscans.org")
