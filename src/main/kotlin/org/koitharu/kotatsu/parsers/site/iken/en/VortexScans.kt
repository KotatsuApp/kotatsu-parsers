package org.koitharu.kotatsu.parsers.site.iken.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.iken.IkenParser

@MangaSourceParser("VORTEXSCANS", "VortexScans", "en")
internal class VortexScans(context: MangaLoaderContext) :
	IkenParser(context, MangaParserSource.VORTEXSCANS, "vortexscans.org") {
	override val selectPages = "main section img"
}
