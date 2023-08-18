package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("COMICSCANS", "Comic Scans", "en")
internal class ComicScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.COMICSCANS, "www.comicscans.org")
