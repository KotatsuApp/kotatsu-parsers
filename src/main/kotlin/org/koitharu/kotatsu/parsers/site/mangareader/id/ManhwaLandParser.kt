package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANHWALAND", "Manhwaland", "id")
internal class ManhwaLandParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHWALAND, pageSize = 20, searchPageSize = 10) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("manhwaland.us", "manhwaland.guru")
	override val listUrl: String = "/manga"
	override val tableMode: Boolean = false
}
