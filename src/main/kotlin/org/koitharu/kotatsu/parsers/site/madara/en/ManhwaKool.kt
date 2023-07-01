package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWAKOOL", "Manhwa Kool", "en")
internal class ManhwaKool(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWAKOOL, "manhwakool.com", pageSize = 10) {

	override val datePattern: String = "MMMM d, yyyy"
}
