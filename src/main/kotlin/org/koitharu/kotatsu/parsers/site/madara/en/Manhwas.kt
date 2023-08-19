package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWAS", "Free Manhwa", "en")
internal class Manhwas(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWAS, "manhwas.com")
