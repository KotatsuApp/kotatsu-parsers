package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("SUSSYSCAN", "SussyScan", "pt")
internal class SussyScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.SUSSYSCAN, "oldi.sussytoons.site")
