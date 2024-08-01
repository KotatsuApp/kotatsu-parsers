package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHUAZONE", "ManhuaZone", "en")
internal class ManhuaZone(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANHUAZONE, "manhuazone.org")
