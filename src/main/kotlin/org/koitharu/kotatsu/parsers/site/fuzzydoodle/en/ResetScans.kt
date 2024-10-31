package org.koitharu.kotatsu.parsers.site.fuzzydoodle.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.fuzzydoodle.FuzzyDoodleParser
import java.util.EnumSet

@MangaSourceParser("RESETSCANS", "ResetScans", "en")
internal class ResetScans(context: MangaLoaderContext) :
	FuzzyDoodleParser(context, MangaParserSource.RESETSCANS, "reset-scans.xyz") {

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
		),
	)
}

