package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MOONWITCHINLOVESCAN", "Moon Witchin Love Scan", "pt")
internal class Moonwitchinlovescan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MOONWITCHINLOVESCAN, "moonwitchinlovescan.com", 10) {

	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
