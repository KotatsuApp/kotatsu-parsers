package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale

@MangaSourceParser("KOMIKLOKALCFD", "Komiklokal Cfd", "id")
internal class KomiklokalCfd(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKLOKALCFD, "komiklokal.cfd", pageSize = 30, searchPageSize = 10) {

	override val isNsfwSource = true
	override val sourceLocale: Locale = Locale.ENGLISH
}
