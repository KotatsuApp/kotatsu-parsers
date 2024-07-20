package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("YURAMANGA", "YuraManga", "id")
internal class YuraManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.YURAMANGA, "yuramanga.my.id") {
	override val sourceLocale: Locale = Locale.ENGLISH
}
