package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KOMIKGUE", "Komikgue", "id")
internal class Komikgue(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.KOMIKGUE, "komikgue.pro", 10) {

	override val tagPrefix = "komik-genre/"
	override val listUrl = "komik/"
	override val datePattern = "MMMM dd, yyyy"
	override val withoutAjax = true
}
