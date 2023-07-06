package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("MANHWALIST", "Manhwalist", "id")
internal class ManhwalistParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHWALIST, pageSize = 24, searchPageSize = 10) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("manhwalist.xyz")

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
}
