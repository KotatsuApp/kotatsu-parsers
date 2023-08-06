package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GOURMETSCANS_ID", "Gourmet Scans Id", "id")
internal class GourmetScansId(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.GOURMETSCANS_ID, "id.gourmetscans.net") {

	override val listUrl = "project/"
	override val tagPrefix = "genre/"
	override val stylepage = ""
}
