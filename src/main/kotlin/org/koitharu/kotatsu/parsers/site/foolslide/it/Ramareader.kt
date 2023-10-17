package org.koitharu.kotatsu.parsers.site.foolslide.it


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.foolslide.FoolSlideParser


@MangaSourceParser("RAMAREADER", "RamaReader", "it")
internal class Ramareader(context: MangaLoaderContext) :
	FoolSlideParser(context, MangaSource.RAMAREADER, "www.ramareader.it") {
	override val searchUrl = "read/search/"
	override val listUrl = "read/directory/"
}
