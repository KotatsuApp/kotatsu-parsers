package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGAKITA", "MangaKita", "id")
internal class MangakKita(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGAKITA, pageSize = 20, searchPageSize = 20) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("mangakita.net")

}
