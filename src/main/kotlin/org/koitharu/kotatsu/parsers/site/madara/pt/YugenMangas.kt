package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("YUGENMANGAS", "Yugen Mangas", "pt")
internal class YugenMangas(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.YUGENMANGAS, "yugenmangas.net.br", 10) {
	override val listUrl = "series/"
}
