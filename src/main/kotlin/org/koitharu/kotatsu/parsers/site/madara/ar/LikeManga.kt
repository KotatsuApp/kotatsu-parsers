package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LIKEMANGA", "LikeManga.net", "ar")
internal class LikeManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LIKEMANGA, "like-manga.net", pageSize = 10)
