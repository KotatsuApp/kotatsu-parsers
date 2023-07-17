package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("YAOIMOBI", "YaoiMobi", "en")
internal class YaoiMobi(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.YAOIMOBI, "yaoi.mobi") {

	override val postreq = true
	override val isNsfwSource = true
}
