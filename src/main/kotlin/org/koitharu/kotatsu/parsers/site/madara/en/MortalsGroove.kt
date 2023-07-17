package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MORTALSGROOVE", "Mortals Groove", "en")
internal class MortalsGroove(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MORTALSGROOVE, "mortalsgroove.com") {

	override val postreq = true
}
