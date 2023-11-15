package org.koitharu.kotatsu.parsers.site.likemanga.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.likemanga.LikeMangaParser

@MangaSourceParser("ZINMANGA_IO", "ZinManga.io", "en")
internal class ZinManga(context: MangaLoaderContext) :
	LikeMangaParser(context, MangaSource.ZINMANGA_IO, "zinmanga.io")
