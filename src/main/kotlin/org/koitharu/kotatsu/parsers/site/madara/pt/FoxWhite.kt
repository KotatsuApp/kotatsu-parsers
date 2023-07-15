package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("FOXWHITE", "Fox White", "pt")
internal class FoxWhite(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.FOXWHITE, "foxwhite.com.br")
