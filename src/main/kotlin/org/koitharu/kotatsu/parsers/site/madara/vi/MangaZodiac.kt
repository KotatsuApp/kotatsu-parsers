package org.koitharu.kotatsu.parsers.site.madara.vi


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("MANGAZODIAC", "Manga Zodiac", "vi")
internal class MangaZodiac(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAZODIAC, "mangazodiac.com")
