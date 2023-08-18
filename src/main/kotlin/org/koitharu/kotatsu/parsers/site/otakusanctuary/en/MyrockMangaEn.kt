package org.koitharu.kotatsu.parsers.site.otakusanctuary.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.otakusanctuary.OtakuSanctuaryParser


@MangaSourceParser("MYROCKMANGA_EN", "MyrockManga En", "en")
internal class MyrockMangaEn(context: MangaLoaderContext) :
	OtakuSanctuaryParser(context, MangaSource.MYROCKMANGA_EN, "myrockmanga.com") {
	override val lang = "us"
}
