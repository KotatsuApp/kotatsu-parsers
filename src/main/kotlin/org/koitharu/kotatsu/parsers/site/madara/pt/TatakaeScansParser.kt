package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TATAKAE_SCANS", "Tatakae Scans", "pt")
internal class TatakaeScansParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TATAKAE_SCANS, "tatakaescan.com", pageSize = 10) {

	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"


}
