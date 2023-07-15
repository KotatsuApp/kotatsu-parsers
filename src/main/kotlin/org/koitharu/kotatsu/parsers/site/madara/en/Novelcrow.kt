package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("NOVELCROW", "Novelcrow", "en")
internal class Novelcrow(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.NOVELCROW, "novelcrow.com", 24) {

	override val isNsfwSource = true
	override val tagPrefix = "comic-genre/"
}
