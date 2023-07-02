package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("GALAXYMANGA", "Galaxymanga", "ar")
internal class Galaxymanga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.GALAXYMANGA, pageSize = 40, searchPageSize = 30) {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("galaxymanga.org")

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("ar", "AR"))

}
