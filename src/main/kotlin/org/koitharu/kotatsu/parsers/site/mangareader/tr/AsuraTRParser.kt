package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("ASURATR", "Asura Scans (tr)", "tr")
internal class AsuraTRParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ASURATR, pageSize = 30, searchPageSize = 10) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("asurascanstr.com")

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("tr"))
}
