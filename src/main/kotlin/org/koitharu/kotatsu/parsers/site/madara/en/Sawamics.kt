package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SAWAMICS", "Sawamics", "en")
internal class Sawamics(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SAWAMICS, "sawamics.com", 10)
