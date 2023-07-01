package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("KOMIKDEWASA", "KomikDewasa", "id")
internal class KomikDewasaParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKDEWASA, pageSize = 20, searchPageSize = 20) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("komikdewasa.cfd")

	override val listUrl: String = "/komik"
	override val isNsfwSource: Boolean = true
}
