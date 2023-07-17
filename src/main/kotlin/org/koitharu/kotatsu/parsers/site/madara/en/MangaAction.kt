package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAACTION", "Manga Action", "en")
internal class MangaAction(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAACTION, "mangaaction.com")
