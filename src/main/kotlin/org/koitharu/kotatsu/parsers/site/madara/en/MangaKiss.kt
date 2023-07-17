package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAKISS", "Manga Kiss", "en")
internal class MangaKiss(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAKISS, "mangakiss.org", 10)
