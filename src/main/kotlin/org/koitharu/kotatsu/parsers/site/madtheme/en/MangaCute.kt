package org.koitharu.kotatsu.parsers.site.madtheme.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madtheme.MadthemeParser

@MangaSourceParser("MANGACUTE", "MangaCute", "en")
internal class MangaCute(context: MangaLoaderContext) :
	MadthemeParser(context, MangaParserSource.MANGACUTE, "mangacute.com")
