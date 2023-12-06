package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALIKE", "MangaLike.net", "ar")
internal class MangaLike(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALIKE, "manga-like.net", pageSize = 10)
