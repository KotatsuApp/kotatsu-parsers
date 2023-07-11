package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PORTALYAOI", "PortalYaoi", "pt")
internal class PortalYaoi(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.PORTALYAOI, "portalyaoi.com", 10) {

	override val isNsfwSource = true
	override val tagPrefix = "genero/"
	override val datePattern: String = "dd/MM"
}
