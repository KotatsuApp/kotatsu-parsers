package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale

@MangaSourceParser("SHIRAKAMI", "Shirakami", "id")
internal class Shirakami(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SHIRAKAMI, "shirakami.xyz", pageSize = 10, searchPageSize = 10) {
	override val sourceLocale: Locale = Locale.ENGLISH
	override val isTagsExclusionSupported = false
}

