package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("COMIZ", "Comiz", "en")
internal class Comiz(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.COMIZ, "v2.comiz.net", 10) {

	override val isNsfwSource = true
}
