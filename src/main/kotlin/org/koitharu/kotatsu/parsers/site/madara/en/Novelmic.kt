package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("NOVELMIC", "NovelMic", "en")
internal class Novelmic(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.NOVELMIC, "novelmic.com", 20) {

	override val postreq = true
}
