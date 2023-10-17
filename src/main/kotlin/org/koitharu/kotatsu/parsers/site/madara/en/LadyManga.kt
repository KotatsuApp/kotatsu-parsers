package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LADYMANGA", "LadyManga", "en")
internal class LadyManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LADYMANGA, "ladymanga.com")
