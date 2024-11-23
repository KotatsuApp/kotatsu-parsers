package org.koitharu.kotatsu.parsers.site.otakusanctuary.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.otakusanctuary.OtakuSanctuaryParser

@MangaSourceParser("OTAKUSAN_EN", "OtakuSan-En", "en")
internal class OtakusanEn(context: MangaLoaderContext) :
	OtakuSanctuaryParser(context, MangaParserSource.OTAKUSAN_EN, "otakusan.me") {
	override val lang = "us"
	override val selectState = ".table-info tr:contains(Status) td"
}
