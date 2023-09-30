package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAKITSU", "Manga Kitsu", "en")
internal class MangaKitsu(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAKITSU, "mangakitsu.com", 20)
