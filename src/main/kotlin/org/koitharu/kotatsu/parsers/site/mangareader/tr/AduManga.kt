package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale

@MangaSourceParser("ADUMANGA", "AduManga", "tr")
internal class AduManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.ADUMANGA, "adumanga.com", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
	override val sourceLocale: Locale = Locale.ENGLISH
}
