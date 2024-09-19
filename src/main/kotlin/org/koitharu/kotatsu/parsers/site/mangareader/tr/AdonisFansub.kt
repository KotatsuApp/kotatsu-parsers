package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ADONISFANSUB", "AdonisFansub", "tr")
internal class AdonisFansub(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaParserSource.ADONISFANSUB,
		"manga.adonisfansub.com",
		pageSize = 20,
		searchPageSize = 20,
	) {

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
		)
}
