package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWA18APP", "Manhwa18 App", "en", ContentType.HENTAI)
internal class Manhwa18App(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWA18APP, "manhwa18.app") {

	override val postreq = true
}
