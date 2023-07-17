package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SUMMANGA", "SumManga", "en")
internal class SumManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SUMMANGA, "summanga.com") {

	override val isNsfwSource = true
}
