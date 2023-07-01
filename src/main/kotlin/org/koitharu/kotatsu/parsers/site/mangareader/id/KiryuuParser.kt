package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("KIRYUU", "Kiryuu", "id")
internal class KiryuuParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KIRYUU, pageSize = 30, searchPageSize = 10) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("kiryuu.id")


	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("in", "ID"))
}
