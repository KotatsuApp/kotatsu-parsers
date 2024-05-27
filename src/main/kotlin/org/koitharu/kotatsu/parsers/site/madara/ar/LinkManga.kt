package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LINKMANGA", "LinkManga.com", "ar")
internal class MangalinkNet(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALINKNET, "link-manga.com", pageSize = 10)
