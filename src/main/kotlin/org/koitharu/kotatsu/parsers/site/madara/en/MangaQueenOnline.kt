package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGA_QUEEN_ONLINE", "Manga Queen Online", "en")
internal class MangaQueenOnline(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGA_QUEEN_ONLINE, "mangaqueen.online", 10)
