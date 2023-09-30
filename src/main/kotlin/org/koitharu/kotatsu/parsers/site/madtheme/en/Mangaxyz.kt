package org.koitharu.kotatsu.parsers.site.madtheme.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madtheme.MadthemeParser

@MangaSourceParser("MANGAXYZ", "Mangaxyz", "en")
internal class Mangaxyz(context: MangaLoaderContext) :
	MadthemeParser(context, MangaSource.MANGAXYZ, "mangaxyz.com")
