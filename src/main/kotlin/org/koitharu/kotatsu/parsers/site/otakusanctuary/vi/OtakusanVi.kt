package org.koitharu.kotatsu.parsers.site.otakusanctuary.vi


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.otakusanctuary.OtakuSanctuaryParser


@MangaSourceParser("OTAKUSAN_VI", "Otakusan Vi", "vi")
internal class OtakusanVi(context: MangaLoaderContext) :
	OtakuSanctuaryParser(context, MangaSource.OTAKUSAN_VI, "otakusan.net") {

	override val selectState = ".table-info tr:contains(Status) td"
	override val lang = "vn"
}
