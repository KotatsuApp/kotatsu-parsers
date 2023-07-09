package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAHUB", "Manga Hub", "fr")
internal class MangaHub(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAHUB, "mangahub.fr") {

	override val isNsfwSource = true
	override val datePattern = "d MMMM yyyy"
}
