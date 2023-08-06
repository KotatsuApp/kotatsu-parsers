package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GOURMETSCANS", "Gourmet Scans", "en")
internal class GourmetScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.GOURMETSCANS, "gourmetscans.net") {

	override val listUrl = "project/"
	override val tagPrefix = "genre/"
	override val stylepage = ""
}
