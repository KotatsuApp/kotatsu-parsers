package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWA18APP", "Manhwa18 App", "en")
internal class Manhwa18App(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWA18APP, "manhwa18.app") {

	override val isNsfwSource = true
	override val postreq = true
}
