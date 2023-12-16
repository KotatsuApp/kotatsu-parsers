package org.koitharu.kotatsu.parsers.site.zeistmanga.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("MAXGSSCAN", "MaxgsScan", "pt")
internal class MaxgsScan(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaSource.MAXGSSCAN, "www.maxgsscan.online")
