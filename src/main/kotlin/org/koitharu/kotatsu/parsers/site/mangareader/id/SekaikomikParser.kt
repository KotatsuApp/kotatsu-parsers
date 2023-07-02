package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("SEKAIKOMIK", "Sekaikomik", "id")
internal class SekaikomikParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SEKAIKOMIK, pageSize = 20, searchPageSize = 100) {
	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("sekaikomik.pro")

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM D, yyyy", Locale("in", "ID"))
}
