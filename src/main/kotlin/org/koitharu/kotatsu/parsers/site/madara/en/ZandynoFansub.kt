package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ZANDYNOFANSUB", "ZandynoFansub", "en")
internal class ZandynoFansub(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ZANDYNOFANSUB, "zandynofansub.aishiteru.org", 20) {

	override val datePattern = "dd.MM.yyyy"
}
