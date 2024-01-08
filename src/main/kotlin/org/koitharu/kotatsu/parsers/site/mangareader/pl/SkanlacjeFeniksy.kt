package org.koitharu.kotatsu.parsers.site.mangareader.pl

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SKANLACJEFENIKSY", "SkanlacjeFeniksy", "pl")
internal class SkanlacjeFeniksy(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaSource.SKANLACJEFENIKSY,
		"skanlacje-feniksy.pl",
		pageSize = 10,
		searchPageSize = 10,
	) {
	override val datePattern = "d MMMM, yyyy"
	override val isTagsExclusionSupported = false
}
