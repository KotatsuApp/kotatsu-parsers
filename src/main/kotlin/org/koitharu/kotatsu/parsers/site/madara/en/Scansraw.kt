package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SCANSRAW", "Scansraw", "en")
internal class Scansraw(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SCANSRAW, "scansraw.com")
