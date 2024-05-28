package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LINKMANGA", "Link-Manga.com", "ar")
internal class LinkManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LINKMANGA, "link-manga.com", pageSize = 10)
