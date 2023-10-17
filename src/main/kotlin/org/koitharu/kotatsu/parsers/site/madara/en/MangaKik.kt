package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAKIK", "MangaKik", "en")
internal class MangaKik(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAKIK, "mangakik.biz", 10)
