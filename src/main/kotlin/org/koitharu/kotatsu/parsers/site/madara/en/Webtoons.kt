package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WEBTOONS", "Webtoons", "en")
internal class Webtoons(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WEBTOONS, "webtoons.top", 20) {

	override val isNsfwSource = true
	override val postreq = true
}
