package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale

@MangaSourceParser("KOMIKTAP", "KomikTap", "id")
internal class KomikTapParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKTAP, "komiktap.me", pageSize = 25, searchPageSize = 10) {
	override val sourceLocale: Locale = Locale.ENGLISH
	override val isTagsExclusionSupported = false
}
