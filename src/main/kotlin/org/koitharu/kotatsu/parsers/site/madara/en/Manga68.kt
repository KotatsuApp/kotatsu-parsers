package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGA68", "Manga68", "en")
internal class Manga68(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGA68, "manga68.com") {

	override val withoutAjax = true
}
