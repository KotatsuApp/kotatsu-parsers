package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HENTAI3Z", "Hentai3z", "en")
internal class Hentai3z(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HENTAI3Z, "hentai3z.xyz", pageSize = 20) {

	override val isNsfwSource = true
	override val withoutAjax = true
}
