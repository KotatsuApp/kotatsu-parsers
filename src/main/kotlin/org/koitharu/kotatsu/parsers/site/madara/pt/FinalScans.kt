package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("FINALSCANS", "Final Scans", "pt")
internal class FinalScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.FINALSCANS, "finalscans.com") {

	override val isNsfwSource = true
}
