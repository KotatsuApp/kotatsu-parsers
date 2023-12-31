package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KENHUAV2SCANK", "Kenhuav2Scan", "es")
internal class Kenhuav2Scan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.KENHUAV2SCANK, "kenhuav2scan.com")
