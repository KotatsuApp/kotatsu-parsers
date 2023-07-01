package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("KOMIKGO", "Komikgo", "id")
internal class KomikgoParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKGO, pageSize = 20, searchPageSize = 10) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("komikgo.org")

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("in", "ID"))

}
