package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ASURASCANS_US", "AsuraScans.us", "en")
internal class AsuraScansUs(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ASURASCANS_US, "asurascans.us") {
	override val listUrl = "comics/"
	override val tagPrefix = "read-en-us-genre/"
}
