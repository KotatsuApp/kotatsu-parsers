package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGANELO_WEBSITE", "Manganelo Website", "en")
internal class ManganeloWebsite(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGANELO_WEBSITE, "manganelo.website", 20) {
	override val postreq = true
}
