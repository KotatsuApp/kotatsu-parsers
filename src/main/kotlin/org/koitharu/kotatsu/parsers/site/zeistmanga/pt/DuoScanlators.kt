package org.koitharu.kotatsu.parsers.site.zeistmanga.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("DUOSCANLATORS", "DuoScanlators", "pt")
internal class DuoScanlators(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.DUOSCANLATORS, "duoscanlators.blogspot.com")
