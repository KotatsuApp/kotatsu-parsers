package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@Broken
@MangaSourceParser("MANHWAPLUS", "ManhwaPlus", "id", ContentType.HENTAI)
internal class ManhwaPlus(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.MANHWAPLUS, "manhwablue.com", 20, 10) {
	override val sourceLocale: Locale = Locale.ENGLISH
	override val listUrl = "/komik"
	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
		)
}
