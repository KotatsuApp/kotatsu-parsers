package org.koitharu.kotatsu.parsers.site.madtheme.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madtheme.MadthemeParser

@Broken
@MangaSourceParser("MANHUASCAN", "kaliscan.io", "en")
internal class ManhuaScan(context: MangaLoaderContext) :
	MadthemeParser(context, MangaParserSource.MANHUASCAN, "manhuascan.io")
