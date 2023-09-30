package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGATYRANT", "Manga Tyrant", "en")
internal class MangaTyrant(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGATYRANT, "mangatyrant.com")
