package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser


@MangaSourceParser("PISCANS", "Piscans", "id")
internal class Piscans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.PISCANS, pageSize = 24, searchPageSize = 24) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("piscans.in")


}
