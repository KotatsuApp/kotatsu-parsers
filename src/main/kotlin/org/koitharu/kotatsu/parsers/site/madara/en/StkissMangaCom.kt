package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("STKISSMANGA_COM", "1St Kiss-Manga .Com", "en")
internal class StkissMangaCom(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.STKISSMANGA_COM, "1stkiss-manga.com", 10)
