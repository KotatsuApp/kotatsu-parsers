package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("YURILIVE", "Yuri Live", "pt")
internal class YuriLive(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.YURILIVE, "yuri.live") {

	override val isNsfwSource = true
	override val tagPrefix = "manga-genero/"
	override val datePattern: String = "dd/MM/yyyy"
}
