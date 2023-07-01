package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser


@MangaSourceParser("SOULSCANS", "Soul Scans", "id")
internal class SoulScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SOULSCANS, pageSize = 30, searchPageSize = 30) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("soulscans.my.id")

}
