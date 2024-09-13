package org.koitharu.kotatsu.parsers.site.zeistmanga.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("XSANOMANGA", "XsanoManga", "ar")
internal class XsanoManga(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.XSANOMANGA, "www.xsano-manga.com")
