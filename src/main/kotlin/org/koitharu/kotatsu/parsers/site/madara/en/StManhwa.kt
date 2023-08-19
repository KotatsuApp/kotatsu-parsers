package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("STMANHWA", "1st Manhwa", "en")
internal class StManhwa(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.STMANHWA, "1stmanhwa.com")
