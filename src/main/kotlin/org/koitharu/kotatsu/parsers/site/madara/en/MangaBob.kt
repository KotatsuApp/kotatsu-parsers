package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGABOB", "Manga Bob", "en")
internal class MangaBob(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGABOB, "mangabob.com") {
	override val postreq = true
}
