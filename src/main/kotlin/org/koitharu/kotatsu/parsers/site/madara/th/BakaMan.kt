package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("BAKAMAN", "BakaMan", "th")
internal class BakaMan(context: MangaLoaderContext) : MadaraParser( context, MangaSource.BAKAMAN, "bakaman.net", pageSize = 18,) {

	override val isNsfwSource = false
}
