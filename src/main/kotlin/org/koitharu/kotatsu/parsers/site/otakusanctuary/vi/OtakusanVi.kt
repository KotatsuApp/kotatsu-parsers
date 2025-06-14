package org.koitharu.kotatsu.parsers.site.otakusanctuary.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.otakusanctuary.OtakuSanctuaryParser
import org.koitharu.kotatsu.parsers.Broken

@Broken("Original site closed")
@MangaSourceParser("OTAKUSAN_VI", "Otaku Sanctuary (VN)", "vi")
internal class OtakusanVi(context: MangaLoaderContext) :
	OtakuSanctuaryParser(context, MangaParserSource.OTAKUSAN_VI, "otakusan.me") {
	override val selectState = ".table-info tr:contains(Status) td"
	override val lang = "vn"
}
