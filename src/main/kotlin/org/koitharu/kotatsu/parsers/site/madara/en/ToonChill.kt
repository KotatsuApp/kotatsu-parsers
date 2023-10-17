package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TOONCHILL", "ToonChill", "en")
internal class ToonChill(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TOONCHILL, "toonchill.com", 32)
