package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TEMPLESCAN", "TempleScan", "en")
internal class TempleScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TEMPLESCAN, "templescan.net") {

	override val datePattern = "dd.MM.yyyy"
	override val tagPrefix = "genre/"
}
