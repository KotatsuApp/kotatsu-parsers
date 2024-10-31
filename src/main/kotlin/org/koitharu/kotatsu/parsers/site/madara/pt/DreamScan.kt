package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("DREAMSCAN", "DreamScan", "pt")
internal class DreamScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.DREAMSCAN, "fairydream.com.br")
