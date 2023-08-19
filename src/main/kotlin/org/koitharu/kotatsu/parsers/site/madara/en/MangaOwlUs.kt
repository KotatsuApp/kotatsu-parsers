package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAOWL_US", "MangaOwl Us (unoriginal)", "en", ContentType.HENTAI)
internal class MangaOwlUs(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAOWL_US, "mangaowl.us")
