package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale

@MangaSourceParser("NOROMAX", "Noromax", "id")
internal class Noromax(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.NOROMAX, "noromax.my.id", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/Komik"
	override val sourceLocale: Locale = Locale.ENGLISH
	override val isTagsExclusionSupported = false
}
