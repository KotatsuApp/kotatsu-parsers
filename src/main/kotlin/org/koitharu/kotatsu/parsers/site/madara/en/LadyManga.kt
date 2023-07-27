package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LADYMANGA", "Lady Manga", "en")
internal class LadyManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LADYMANGA, "ladymanga.com")
