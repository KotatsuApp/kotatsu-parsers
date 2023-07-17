package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WAKAMICS", "Wakamics", "en")
internal class Wakamics(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WAKAMICS, "wakamics.net", 10)
