package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAOWLBLOG", "Manga Owl .Blog", "en", ContentType.HENTAI)
internal class MangaOwlBlog(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAOWLBLOG, "mangaowl.blog", 20) {
	override val postReq = true
}
