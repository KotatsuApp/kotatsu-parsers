package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("DOJING", "Dojing", "id")
internal class Dojing(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.DOJING, pageSize = 12, searchPageSize = 12) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("dojing.net")


	override val isNsfwSource = true

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("in", "ID"))

}
