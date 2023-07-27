package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWA18ORG", "Manhwa18 Org", "en", ContentType.HENTAI)
internal class Manhwa18Org(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWA18ORG, "manhwa18.org") {

	override val postreq = true
}
