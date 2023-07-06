package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("BANANASCAN", "Banana Scan", "fr")
internal class BananaScan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.BANANASCAN, pageSize = 20, searchPageSize = 20) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("banana-scan.com")

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.FRENCH)

}
