package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ASTRALLIBRARY", "Astral Library", "en")
internal class Astrallibrary(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ASTRALLIBRARY, "astrallibrary.net", 18) {
	override val datePattern = "dd MMM"
	override val postReq = true
}
