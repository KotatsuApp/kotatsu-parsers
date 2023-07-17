package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HENTAIMANGA", "Hentai Manga", "en")
internal class HentaiManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HENTAIMANGA, "hentaimanga.me", 36) {

	override val isNsfwSource = true
	override val postreq = true
}
