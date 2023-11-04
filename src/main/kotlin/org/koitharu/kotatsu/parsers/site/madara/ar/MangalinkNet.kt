package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALINKNET", "MangaLink.net", "ar")
internal class MangalinkNet(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALINKNET, "manga-link.net", pageSize = 10)
