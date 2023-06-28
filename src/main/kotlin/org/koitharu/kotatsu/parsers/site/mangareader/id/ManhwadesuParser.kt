package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANHWADESU", "ManhwaDesu", "id")
internal class ManhwadesuParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHWADESU, pageSize = 20, searchPageSize = 10) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("manhwadesu.pro", "manhwadesu.org")

	override val listUrl: String get() = "/komik"
	override val tableMode: Boolean get() = false
}
