package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGADIYARI", "MangaDiyari", "tr")
internal class MangaDiyari(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGADIYARI, "manga-diyari.com", 10)
