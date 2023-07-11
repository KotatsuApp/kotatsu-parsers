package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("LEGIONSCANS", "Legion Scans", "es")
internal class LegionScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.LEGIONSCANS, pageSize = 20, searchPageSize = 20) {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("legionscans.com")

}

