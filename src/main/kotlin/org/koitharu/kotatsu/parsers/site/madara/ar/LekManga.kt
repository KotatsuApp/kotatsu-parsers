package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALEK", "LekManga", "ar")
internal class MangaLek(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALEK, "lek-manga.net", pageSize = 10)
