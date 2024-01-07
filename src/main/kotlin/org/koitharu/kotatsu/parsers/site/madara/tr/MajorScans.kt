package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MAJORSCANS", "MajorScans", "tr")
internal class MajorScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MAJORSCANS, "www.majorscans.com", pageSize = 18) {
	override val datePattern = "dd/MM/yyyy"
}
