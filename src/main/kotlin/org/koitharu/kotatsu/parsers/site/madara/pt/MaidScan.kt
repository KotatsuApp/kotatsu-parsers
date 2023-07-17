package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MAIDSCAN", "MaidScan", "pt")
internal class MaidScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MAIDSCAN, "maidscan.com.br", 10) {

	override val isNsfwSource = true
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
