package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("DRAKESCANS", "Drake Scans", "en")
internal class DrakeScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.DRAKESCANS, "drakescans.com", 10) {

	override val datePattern = "dd/MM/yyyy"
	override val tagPrefix = "series-genre/"
	override val listUrl = "series/"
}
