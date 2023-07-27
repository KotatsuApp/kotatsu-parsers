package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAKIO", "Manga Kio", "en")
internal class MangaKio(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAKIO, "mangakio.me", 10)
