package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALIKE", "Manga Like", "ar")
internal class MangaLike(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALIKE, "mangalike.me", pageSize = 10)
