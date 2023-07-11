package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("VFSCAN", "Vf Scan", "fr")
internal class VfScan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.VFSCAN, pageSize = 18, searchPageSize = 18) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("www.vfscan.com")

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH)
}
