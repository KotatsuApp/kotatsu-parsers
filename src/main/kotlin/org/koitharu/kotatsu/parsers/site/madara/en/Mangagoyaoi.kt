package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAGOYAOI", "Manga Go Yaoi", "en", ContentType.HENTAI)
internal class Mangagoyaoi(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAGOYAOI, "mangagoyaoi.com")
