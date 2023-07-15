package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANYTOONME", "Many Toon Me", "en")
internal class ManyToonMe(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANYTOONME, "manytoon.me", 20) {

	override val isNsfwSource = true
}
