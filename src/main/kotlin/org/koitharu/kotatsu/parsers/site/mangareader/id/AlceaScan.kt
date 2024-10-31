package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@Broken
@MangaSourceParser("ALCEASCAN", "AlceaScan", "id")
internal class AlceaScan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.ALCEASCAN, "alceacomic.my.id", pageSize = 20, searchPageSize = 10) {
	override val sourceLocale: Locale = Locale.ENGLISH
}
