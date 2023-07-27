package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ASTRALLIBRARY", "Astrallibrary", "en")
internal class Astrallibrary(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ASTRALLIBRARY, "astrallibrary.net", 18) {

	override val datePattern = "dd MMM"
	override val postreq = true
}
