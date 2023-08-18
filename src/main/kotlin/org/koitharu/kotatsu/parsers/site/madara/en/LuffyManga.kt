package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LUFFYMANGA", "Luffy Manga", "en")
internal class LuffyManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LUFFYMANGA, "luffymanga.com", 10)
