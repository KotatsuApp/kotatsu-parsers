package org.koitharu.kotatsu.parsers.site.zeistmanga.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("WOLFSCANBR", "WolfScanBr", "pt")
internal class WolfScanBr(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaSource.WOLFSCANBR, "wolfscanbr.blogspot.com")
