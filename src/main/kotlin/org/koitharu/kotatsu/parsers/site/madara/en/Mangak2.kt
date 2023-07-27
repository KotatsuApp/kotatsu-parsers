package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAK2", "Mangak2", "en", ContentType.HENTAI)
internal class Mangak2(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAK2, "mangak2.com", 10)
