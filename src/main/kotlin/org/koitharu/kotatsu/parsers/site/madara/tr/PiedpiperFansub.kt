package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("PIEDPIPERFANSUB", "PiedpiperFansub", "tr")
internal class PiedpiperFansub(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.PIEDPIPERFANSUB, "piedpiperfansub.me", 18) {
	override val datePattern = "d MMMM yyyy"
}
