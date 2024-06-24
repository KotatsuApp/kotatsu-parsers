package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGARYU", "MangaRyu", "en", ContentType.HENTAI)
internal class MangaRyu(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGARYU, "mangaryu.com", 10)
