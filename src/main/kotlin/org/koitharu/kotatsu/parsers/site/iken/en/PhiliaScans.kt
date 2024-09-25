package org.koitharu.kotatsu.parsers.site.iken.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.iken.IkenParser

@MangaSourceParser("PHILIASCANS", "PhiliaScans", "en")
internal class PhiliaScans(context: MangaLoaderContext) :
	IkenParser(context, MangaParserSource.PHILIASCANS, "philiascans.com") {
	override val selectPages = "main section img"
}
