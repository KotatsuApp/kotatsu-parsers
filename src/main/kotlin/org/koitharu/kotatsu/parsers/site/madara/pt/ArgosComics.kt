package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.Broken

@Broken
@MangaSourceParser("ARGOSCOMICS", "ArgosComics", "pt")
internal class ArgosComics(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ARGOSCOMICS, "argoscomic.com")
