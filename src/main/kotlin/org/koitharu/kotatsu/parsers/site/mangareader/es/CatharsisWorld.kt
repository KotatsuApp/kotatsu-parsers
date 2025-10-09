package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale
import org.koitharu.kotatsu.parsers.Broken

@Broken("Not dead, changed template")
@MangaSourceParser("CATHARSISWORLD", "CatharsisWorld", "es")
internal class CatharsisWorld(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.CATHARSISWORLD, "catharsisworld.dig-it.info", pageSize = 30, searchPageSize = 10) {
	override val sourceLocale: Locale = Locale.ENGLISH
	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
		)
}
