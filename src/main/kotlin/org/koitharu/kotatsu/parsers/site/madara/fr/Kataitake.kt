package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KATAITAKE", "Kataitake", "fr", ContentType.HENTAI)
internal class Kataitake(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.KATAITAKE, "www.kataitake.fr", 10) {
	override val datePattern = "dd/MM/yyyy"
	override val tagPrefix = "genre/"
	override val postReq = true
}
