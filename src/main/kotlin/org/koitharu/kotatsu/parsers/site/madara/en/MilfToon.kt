package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MILFTOON", "Milf Toon", "en")
internal class MilfToon(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MILFTOON, "milftoon.xxx", 20) {

	override val isNsfwSource = true
	override val postreq = true
	override val datePattern = "d MMMM, yyyy"
}
