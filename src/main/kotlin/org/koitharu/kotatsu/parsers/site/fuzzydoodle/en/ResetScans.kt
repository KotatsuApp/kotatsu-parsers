package org.koitharu.kotatsu.parsers.site.fuzzydoodle.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.*

@MangaSourceParser("RESETSCANS", "ResetScans", "en")
internal class ResetScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.RESETSCANS, domain = "rspro.xyz", pageSize = 18) {

	override suspend fun getFilterOptions() = super.getFilterOptions().copy(
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
		),
	)
}

