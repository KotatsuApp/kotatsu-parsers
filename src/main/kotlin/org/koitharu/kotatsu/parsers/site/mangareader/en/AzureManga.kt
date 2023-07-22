package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser


@MangaSourceParser("AZUREMANGA", "Azure Manga", "en")
internal class AzureManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.AZUREMANGA, "azuremanga.com", pageSize = 20, searchPageSize = 10) {

	override val datePattern = "MMM d, yyyy"
}
