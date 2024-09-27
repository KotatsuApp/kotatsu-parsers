package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("MOONWITCHINLOVESCAN", "MoonWitchinScan", "pt")
internal class Moonwitchinlovescan(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MOONWITCHINLOVESCAN, "moonwitchscan.com.br", 10) {
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
