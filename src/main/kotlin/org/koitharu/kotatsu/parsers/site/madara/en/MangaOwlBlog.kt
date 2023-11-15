package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAOWLBLOG", "MangaOwlnet.com", "en")
internal class MangaOwlBlog(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAOWLBLOG, "mangaowlnet.com")
