package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("GOLGEBAHCESI", "GolgeBahcesi", "tr")
internal class Golgebahcesi(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.GOLGEBAHCESI, "golgebahcesi.com", pageSize = 14, searchPageSize = 9) {
	override val isTagsExclusionSupported = false
}
