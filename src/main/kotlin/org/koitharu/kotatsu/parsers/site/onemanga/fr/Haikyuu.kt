package org.koitharu.kotatsu.parsers.site.onemanga.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.onemanga.OneMangaParser

@MangaSourceParser("HAIKYUU", "Haikyuu", "fr")
internal class Haikyuu(context: MangaLoaderContext) :
	OneMangaParser(context, MangaParserSource.HAIKYUU, "haikyuu.fr")
