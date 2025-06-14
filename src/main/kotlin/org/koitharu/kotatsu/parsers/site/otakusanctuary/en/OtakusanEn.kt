package org.koitharu.kotatsu.parsers.site.otakusanctuary.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.otakusanctuary.OtakuSanctuaryParser
import org.koitharu.kotatsu.parsers.Broken

@Broken("Original site closed")
@MangaSourceParser("OTAKUSAN_EN", "Otaku Sanctuary (EN)", "en")
internal class OtakusanEn(context: MangaLoaderContext) :
	OtakuSanctuaryParser(context, MangaParserSource.OTAKUSAN_EN, "otakusan.me") {
	override val lang = "us"
	override val selectState = ".table-info tr:contains(Status) td"
}
