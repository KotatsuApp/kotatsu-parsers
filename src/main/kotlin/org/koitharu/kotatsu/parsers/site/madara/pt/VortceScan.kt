package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("VORTCESCAN", "Vortce Scan", "pt")
internal class VortceScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.VORTCESCAN, "vortcescan.com.br", pageSize = 10) {
	override val datePattern: String = "d 'de' MMMMM 'de' yyyy"
}
