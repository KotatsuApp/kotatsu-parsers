package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ANIGLISCANS", "Anigli Scans", "en")
internal class AnigliScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ANIGLISCANS, pageSize = 47, searchPageSize = 47) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("anigliscans.com")

	override val listUrl: String
		get() = "/series"

}
