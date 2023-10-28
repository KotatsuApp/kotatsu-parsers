package org.koitharu.kotatsu.parsers.site.otakusanctuary.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.otakusanctuary.OtakuSanctuaryParser

@MangaSourceParser("OTAKUSAN_EN", "OtakuSan-En", "en")
internal class OtakusanEn(context: MangaLoaderContext) :
	OtakuSanctuaryParser(context, MangaSource.OTAKUSAN_EN, "otakusan.net") {
	override val lang = "us"
	override val selectState = ".table-info tr:contains(Status) td"
}
