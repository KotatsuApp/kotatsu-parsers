package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LOLICONMOBI", "LoliconMobi", "en", ContentType.HENTAI)
internal class LoliconMobi(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LOLICONMOBI, "lolicon.mobi") {

	override val postreq = true
	override val tagPrefix = "lolicon-genre/"
}
