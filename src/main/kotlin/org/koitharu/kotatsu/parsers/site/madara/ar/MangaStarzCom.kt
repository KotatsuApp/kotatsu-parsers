package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGASTARZCOM", "MangaStarz Com", "ar")
internal class MangaStarzCom(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGASTARZCOM, "mangastarz.com", 10) {
	override val datePattern = "d MMMM، yyyy"
}
