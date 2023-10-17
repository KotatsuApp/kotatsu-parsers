package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LKSCANLATION", "LkScanlation", "es")
internal class LkScanlation(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LKSCANLATION, "lkscanlation.com") {
	override val tagPrefix = "manhwa-genre/"
	override val listUrl = "manhwa/"
}
