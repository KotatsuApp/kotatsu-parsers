package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("STICKHORSE", "Stickhorse", "es")
internal class Stickhorse(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.STICKHORSE, "www.stickhorse.cl") {

	override val postreq = true
}
