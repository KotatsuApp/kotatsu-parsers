package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ALLIED_FANSUB", "Allied Fansub", "tr", ContentType.HENTAI)
internal class AlliedFansub(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ALLIED_FANSUB, "alliedfansub.online", 20) {
	override val datePattern = "dd/MM/yyyy"
}
