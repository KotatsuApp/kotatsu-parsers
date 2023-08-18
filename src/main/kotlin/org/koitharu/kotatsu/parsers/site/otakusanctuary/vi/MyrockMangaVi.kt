package org.koitharu.kotatsu.parsers.site.otakusanctuary.vi


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.otakusanctuary.OtakuSanctuaryParser


@MangaSourceParser("MYROCKMANGA_VI", "MyrockManga Vi", "en")
internal class MyrockMangaVi(context: MangaLoaderContext) :
	OtakuSanctuaryParser(context, MangaSource.MYROCKMANGA_VI, "myrockmanga.com") {
	override val lang = "vn"
}

