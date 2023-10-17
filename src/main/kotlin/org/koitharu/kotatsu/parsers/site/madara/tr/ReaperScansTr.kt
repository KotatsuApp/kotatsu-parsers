package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("REAPERSCANSTR", "ReaperScans", "tr")
internal class ReaperScansTr(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.REAPERSCANSTR, "reaperscanstr.com", 5) {
	override val listUrl = "seri/"
}
