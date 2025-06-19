package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("SUSSYSTOONS", "Sussytoons", "pt")
internal class SussyToons(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.SUSSYTOONS, "www.sussytoons.wtf")
