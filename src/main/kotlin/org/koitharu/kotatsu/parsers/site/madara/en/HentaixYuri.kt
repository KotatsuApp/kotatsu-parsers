package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HENTAIXYURI", "Hentai x Yuri", "en")
internal class HentaixYuri(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HENTAIXYURI, "hentaixyuri.com", 16) {

	override val datePattern = "MMMM d, yyyy"
	override val isNsfwSource = true
	override val postreq = true
}
