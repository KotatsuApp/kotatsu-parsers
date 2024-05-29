package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALEKO", "Manga-Leko.org", "ar")
internal class MangaLeko(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALEKO, "manga-leko.org", pageSize = 10)
