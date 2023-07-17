package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALIONZ", "Manga Lionz", "ar")
internal class MangaLionz(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALIONZ, "mangalionz.com", pageSize = 10)
