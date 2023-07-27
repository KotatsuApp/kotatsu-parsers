package org.koitharu.kotatsu.parsers.site.madara.ar


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("FALCONMANGA", "FalconManga", "ar")
internal class FalconManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.FALCONMANGA, "falconmanga.com") {

	override val datePattern = "d MMMM، yyyy"
}
