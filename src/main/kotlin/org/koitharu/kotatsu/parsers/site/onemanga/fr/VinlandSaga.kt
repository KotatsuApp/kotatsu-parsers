package org.koitharu.kotatsu.parsers.site.onemanga.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.onemanga.OneMangaParser

@MangaSourceParser("VINLANDSAGA", "VinlandSaga", "fr")
internal class VinlandSaga(context: MangaLoaderContext) :
	OneMangaParser(context, MangaParserSource.VINLANDSAGA, "vinlandsaga.fr")
