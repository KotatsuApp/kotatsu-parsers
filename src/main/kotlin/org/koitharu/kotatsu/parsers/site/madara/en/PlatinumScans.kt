package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PLATINUMSCANS", "PlatinumScans", "en")
internal class PlatinumScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.PLATINUMSCANS, "platinumscans.com", pageSize = 10) {
	override val postReq = true
}
