package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWA2READ", "Manhwa2Read", "en")
internal class Manhwa2Read(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWA2READ, "manhwa2read.com")
