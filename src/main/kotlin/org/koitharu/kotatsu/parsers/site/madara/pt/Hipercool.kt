package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("HIPERCOOL", "Hipercool", "pt")
internal class Hipercool(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HIPERCOOL, "hipercool.xyz", pageSize = 20) {

	override val datePattern = "MMMM d, yyyy"

	override val tagPrefix = "manga-tag/"

	override val isNsfwSource = true

}
