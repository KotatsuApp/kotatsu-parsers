package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LOLICONMOBI", "LoliconMobi", "en")
internal class LoliconMobi(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LOLICONMOBI, "lolicon.mobi") {

	override val postreq = true
	override val isNsfwSource = true
	override val tagPrefix = "lolicon-genre/"
	override val datePattern = "MMMM d, yyyy"
}
