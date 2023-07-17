package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAFORFREE", "MangaForFree", "en")
internal class MangaForFree(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAFORFREE, "mangaforfree.com", 10) {

	override val postreq = true
	override val isNsfwSource = true
}
