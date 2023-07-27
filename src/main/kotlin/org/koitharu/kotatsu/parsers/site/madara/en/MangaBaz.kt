package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGABAZ", "MangaBaz", "en")
internal class MangaBaz(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGABAZ, "mangabaz.net")
