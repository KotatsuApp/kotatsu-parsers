package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale

@MangaSourceParser("KOMIKINDO_MOE", "KomikIndo.moe", "id")
internal class KomikIndo(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.KOMIKINDO_MOE, "komikindo.moe", pageSize = 20, searchPageSize = 10) {
	override val sourceLocale: Locale = Locale.ENGLISH
}
