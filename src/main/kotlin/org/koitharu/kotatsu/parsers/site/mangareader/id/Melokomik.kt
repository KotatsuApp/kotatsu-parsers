package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("MELOKOMIK", "Melokomik", "id")
internal class Melokomik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MELOKOMIK, pageSize = 20, searchPageSize = 20) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("melokomik.xyz")


	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("in", "ID"))

}
