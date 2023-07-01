package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("KOMIKMANGA", "KomikManga", "id")
internal class KomikMangaParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKMANGA, pageSize = 20, searchPageSize = 10) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("komikhentai.co")

	override val listUrl: String
		get() = "/project"

}
