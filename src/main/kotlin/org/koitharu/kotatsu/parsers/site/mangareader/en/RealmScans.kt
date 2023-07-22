package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser


@MangaSourceParser("REALMSCANS", "RealmScans", "en")
internal class RealmScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.REALMSCANS, "realmscans.xyz", pageSize = 30, searchPageSize = 50) {

	override val listUrl = "/series"
	override val datePattern = "dd MMM yyyy"
}
