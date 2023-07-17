package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("AQUAMANGA", "AquaManga", "en")
internal class AquaManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.AQUAMANGA, "aquamanga.com") {

	override val datePattern = "MMMM dd, yyyy"
	override val withoutAjax = true

}
