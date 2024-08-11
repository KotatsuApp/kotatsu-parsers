package org.koitharu.kotatsu.parsers.site.mangaworld.it

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangaworld.MangaWorldParser

@MangaSourceParser("MANGAWORLDADULT", "MangaWorldAdult", "it")
internal class MangaWorldAdult(
	context: MangaLoaderContext,
) : MangaWorldParser(context, MangaParserSource.MANGAWORLDADULT, "mangaworldadult.net")
