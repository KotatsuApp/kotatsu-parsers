package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HUNTERSSCAN", "Hunters Scan", "pt")
internal class HuntersScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HUNTERSSCAN, "huntersscan.xyz", pageSize = 50) {
	override val withoutAjax = true
	override val datePattern = "MM/dd/yyyy"
	override val tagPrefix = "series-genre/"
	override val listUrl = "series/"
}
