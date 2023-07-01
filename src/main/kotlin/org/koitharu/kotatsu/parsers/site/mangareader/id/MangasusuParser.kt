package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser


@MangaSourceParser("MANGASUSU", "Mangasusu", "id")
internal class MangasusuParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGASUSU, pageSize = 20, searchPageSize = 10) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("mangasusu.co.in")

	override val listUrl: String
		get() = "/project"


}
