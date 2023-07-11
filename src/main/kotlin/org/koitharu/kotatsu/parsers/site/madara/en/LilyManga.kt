package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LILYMANGA", "LilyManga", "en")
internal class LilyManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LILYMANGA, "lilymanga.net") {

	override val isNsfwSource = true
	override val tagPrefix = "ys-genre/"
	override val datePattern = "yyyy-MM-dd"
}
