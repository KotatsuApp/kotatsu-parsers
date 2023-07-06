package org.koitharu.kotatsu.parsers.site.madara.ru


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("BEST_MANGA", "best manga", "ru")
internal class BestManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.BEST_MANGA, "bestmanga.club") {

	override val datePattern = "dd.MM.yyyy"
	override val postreq = true
}
