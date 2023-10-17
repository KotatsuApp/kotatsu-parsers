package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHUAFENIX", "ManhuaFenix", "es")
internal class ManhuaFenix(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHUAFENIX, "manhua-fenix.com") {

	override val postReq = true
}
