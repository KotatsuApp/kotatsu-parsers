package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAGOYAOI", "Mangagoyaoi", "en")
internal class Mangagoyaoi(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAGOYAOI, "mangagoyaoi.com") {

	override val isNsfwSource = true
}
