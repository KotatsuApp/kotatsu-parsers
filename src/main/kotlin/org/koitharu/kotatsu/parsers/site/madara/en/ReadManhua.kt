package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("READMANHUA", "ReadManhua", "en", ContentType.HENTAI)
internal class ReadManhua(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.READMANHUA, "readmanhua.net", 20) {
	override val postReq = true
	override val datePattern = "d MMM yy"
}
