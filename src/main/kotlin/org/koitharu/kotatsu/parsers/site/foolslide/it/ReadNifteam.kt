package org.koitharu.kotatsu.parsers.site.foolslide.it


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.foolslide.FoolSlideParser


@MangaSourceParser("READNIFTEAM", "ReadNifTeam", "it")
internal class ReadNifteam(context: MangaLoaderContext) :
	FoolSlideParser(context, MangaSource.READNIFTEAM, "read-nifteam.info") {
	override val searchUrl = "slide/search/"
	override val listUrl = "slide/directory/"
}
