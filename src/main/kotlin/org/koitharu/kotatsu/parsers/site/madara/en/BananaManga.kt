package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("BANANA_MANGA", "Banana Manga", "en")
internal class BananaManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.BANANA_MANGA, "bananamanga.net", 20)
