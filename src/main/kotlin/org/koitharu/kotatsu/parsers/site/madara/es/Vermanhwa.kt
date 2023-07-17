package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("VERMANHWA", "Vermanhwa", "es")
internal class Vermanhwa(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.VERMANHWA, "vermanhwa.es", 10) {

	override val isNsfwSource = true
	override val withoutAjax = true
}
