package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGA_ONLINE", "Manga Online", "en")
internal class MangaOnline(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGA_ONLINE, "mangaonline.team", 18)
