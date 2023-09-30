package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("COPYPASTESCAN", "CopyPaste Scan", "es")
internal class Copypastescan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.COPYPASTESCAN, "copypastescan.xyz", 10) {
	override val datePattern = "d MMMM, yyyy"
}
