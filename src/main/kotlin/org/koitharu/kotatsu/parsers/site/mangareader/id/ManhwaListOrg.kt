package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale

@MangaSourceParser("MANHWALIST_ORG", "ManhwaList.org", "id")
internal class ManhwaListOrg(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaParserSource.MANHWALIST_ORG,
		"isekaikomik.com",
		pageSize = 30,
		searchPageSize = 10,
	) {
	override val sourceLocale: Locale = Locale.ENGLISH
	override val listUrl = "/manga"
	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
		)
}
