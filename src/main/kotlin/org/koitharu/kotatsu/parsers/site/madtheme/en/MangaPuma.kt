package org.koitharu.kotatsu.parsers.site.madtheme.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madtheme.MadthemeParser

@MangaSourceParser("MANGAPUMA", "MangaPuma", "en")
internal class MangaPuma(context: MangaLoaderContext) :
	MadthemeParser(context, MangaSource.MANGAPUMA, "mangapuma.com")
