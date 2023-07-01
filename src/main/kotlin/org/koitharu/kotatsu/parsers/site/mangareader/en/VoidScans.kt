package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("VOIDSCANS", "Void Scans", "en")
internal class VoidScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.VOIDSCANS, pageSize = 150, searchPageSize = 150) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("void-scans.com")
}
