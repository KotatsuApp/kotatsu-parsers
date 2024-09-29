package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser

@MangaSourceParser("DOCTRUYEN3Q", "DocTruyen3Q", "vi")
internal class DocTruyen3Q(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.DOCTRUYEN3Q, "doctruyen3qto.pro", 36)
