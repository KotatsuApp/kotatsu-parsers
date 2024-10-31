package org.koitharu.kotatsu.parsers.site.zeistmanga.pt

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@Broken
@MangaSourceParser("MAXGSSCAN", "MaxgsScan", "pt")
internal class MaxgsScan(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.MAXGSSCAN, "www.maxgsscan.online")
