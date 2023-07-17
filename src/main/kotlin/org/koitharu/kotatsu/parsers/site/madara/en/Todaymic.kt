package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TODAYMIC", "Todaymic", "en")
internal class Todaymic(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TODAYMIC, "todaymic.com")
