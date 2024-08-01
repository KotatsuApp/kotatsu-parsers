package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TORTUGACEVIRI", "TortugaCeviri", "tr")
internal class TortugaCeviri(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.TORTUGACEVIRI, "tortuga-ceviri.com")
