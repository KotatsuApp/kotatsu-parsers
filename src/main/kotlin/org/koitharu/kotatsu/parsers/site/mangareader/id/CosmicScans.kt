package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("COSMIC_SCANS", "CosmicScans.id", "id")
internal class CosmicScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.COSMIC_SCANS, "cosmicscans.id", pageSize = 30, searchPageSize = 30) {
	override val sourceLocale: Locale = Locale.ENGLISH
	override val isTagsExclusionSupported = false
}
