package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGASUSUKU", "MangaSusuku", "id")
internal class MangaSusuku(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGASUSUKU, pageSize = 20, searchPageSize = 20) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("mangasusuku.xyz")

	override val listUrl: String
		get() = "/komik"

	override val isNsfwSource = true
}
