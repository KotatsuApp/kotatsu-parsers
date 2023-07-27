package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGATONE", "MangaTone", "en")
internal class MangaTone(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGATONE, "mangatone.com") {

	override val postreq = true

}
