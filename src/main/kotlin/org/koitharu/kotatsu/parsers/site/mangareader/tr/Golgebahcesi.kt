package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("GOLGEBAHCESI", "Golgebahcesi", "tr")
internal class Golgebahcesi(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.GOLGEBAHCESI, pageSize = 14, searchPageSize = 9) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("golgebahcesi.com")

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr"))
}
