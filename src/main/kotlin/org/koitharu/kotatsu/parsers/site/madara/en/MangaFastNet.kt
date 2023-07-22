package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAFASTNET", "Manga Fast Net", "en")
internal class MangaFastNet(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAFASTNET, "manhuafast.net") {

	override val withoutAjax = true
}
