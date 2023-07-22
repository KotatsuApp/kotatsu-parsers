package org.koitharu.kotatsu.parsers.site.madara.tr


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("NIVERAFANSUB", "Nivera Fansub", "tr")
internal class NiveraFansub(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.NIVERAFANSUB, "niverafansub.com") {

	override val datePattern = "d MMMM yyyy"
	override val isNsfwSource = true
}
