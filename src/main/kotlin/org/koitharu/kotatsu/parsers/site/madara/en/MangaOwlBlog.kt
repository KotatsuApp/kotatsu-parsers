package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("MANGAOWLBLOG", "MangaOwlnet.com", "en")
internal class MangaOwlBlog(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGAOWLBLOG, "mangaowlnet.com")
