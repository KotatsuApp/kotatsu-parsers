package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ANISA_MANGA", "Anisa Manga", "tr")
internal class AnisaManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ANISA_MANGA, "anisamanga.com")
