package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser


@MangaSourceParser("BIRDMANGA", "Bird Manga", "en")
internal class BirdManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.BIRDMANGA, "birdmanga.com", pageSize = 20, searchPageSize = 10) {
	override val encodedSrc = true
}
