package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale

@MangaSourceParser("OTSUGAMI", "Otsugami", "id")
internal class Otsugami(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.OTSUGAMI, "otsugami.id", pageSize = 40, searchPageSize = 10) {

	override val sourceLocale: Locale = Locale.ENGLISH

}
