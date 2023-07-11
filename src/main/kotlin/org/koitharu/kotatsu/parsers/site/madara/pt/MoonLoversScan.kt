package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MOONLOVERSSCAN", "Moon Lovers Scan", "pt")
internal class MoonLoversScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MOONLOVERSSCAN, "moonloversscan.com.br", 10) {

	override val isNsfwSource = true
	override val datePattern = "MMMM d, yyyy"
}
