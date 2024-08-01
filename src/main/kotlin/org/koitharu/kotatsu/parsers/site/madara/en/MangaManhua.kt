package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGA_MANHUA", "MangaManhua", "en")
internal class MangaManhua(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGA_MANHUA, "mangaonlineteam.com", pageSize = 10)
