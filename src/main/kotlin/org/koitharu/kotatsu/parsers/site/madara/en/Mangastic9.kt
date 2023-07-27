package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGASTIC9", "Mangastic9", "en")
internal class Mangastic9(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGASTIC9, "mangastic.cc", 20)
