package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HENTAI_4FREE", "Hentai4Free", "en")
internal class Hentai4Free(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HENTAI_4FREE, "hentai4free.net", pageSize = 24) {

	override val tagPrefix = "hentai-tag/"
	override val datePattern = "MMMM dd, yyyy"
	override val isNsfwSource = true

}
