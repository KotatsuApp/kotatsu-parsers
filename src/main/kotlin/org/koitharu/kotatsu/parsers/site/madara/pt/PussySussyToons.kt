package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PUSSYSUSSYTOONS", "PussySussyToons", "pt", ContentType.HENTAI)
internal class PussySussyToons(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.PUSSYSUSSYTOONS, "pussy.sussytoons.com")
