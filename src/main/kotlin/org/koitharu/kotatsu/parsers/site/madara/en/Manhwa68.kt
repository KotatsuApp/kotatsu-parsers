package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWA68", "Manhwa 68", "en")
internal class Manhwa68(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWA68, "manhwa68.com") {

	override val isNsfwSource = true
}
