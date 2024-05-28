package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LIKEMANGANET", "Like-Manga.net", "ar")
internal class MangaLikeNet(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LIKEMANGANET, "like-manga.net", pageSize = 10)
