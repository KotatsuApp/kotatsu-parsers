package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("COFFEE_MANGA", "Coffee Manga", "en")
internal class CoffeeManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.COFFEE_MANGA, "coffeemanga.io")
