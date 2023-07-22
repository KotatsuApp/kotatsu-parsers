package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser


@MangaSourceParser("KUMASCANS", "Kuma Scans", "en")
internal class KumaScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KUMASCANS, "kumascans.com", pageSize = 20, searchPageSize = 20) {

	override val datePattern = "MMM d, yyyy"
}
