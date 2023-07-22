package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale

@MangaSourceParser("MANHWAINDOICU", "Manhwa Indo Icu", "id")
internal class ManhwaIndoIcu(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHWAINDOICU, "manhwaindo.icu", pageSize = 30, searchPageSize = 10) {

	override val isNsfwSource = true
	override val sourceLocale: Locale = Locale.ENGLISH
}
