package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("BOYS_LOVE", "Boys Love", "en")
internal class BoysLove(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.BOYS_LOVE, "boyslove.me", 20) {

	override val isNsfwSource = true
	override val tagPrefix = "boyslove-genre/"
	override val datePattern = "MMMM d, yyyy"
	override val postreq = true
}
