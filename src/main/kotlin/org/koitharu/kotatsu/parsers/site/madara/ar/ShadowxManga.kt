package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SHADOWXMANGA", "Shadow X Manga", "ar")
internal class ShadowxManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SHADOWXMANGA, "shadowxmanga.com") {

	override val datePattern = "yyyy/MM/dd"
}
