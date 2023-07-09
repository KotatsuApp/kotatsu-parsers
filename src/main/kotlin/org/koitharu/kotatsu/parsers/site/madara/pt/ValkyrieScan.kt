package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("VALKYRIESCAN", "Valkyrie Scan", "pt")
internal class ValkyrieScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.VALKYRIESCAN, "valkyriescan.com", pageSize = 10) {

	override val isNsfwSource = true
	override val datePattern: String = "dd/MM/yyyy"
}
