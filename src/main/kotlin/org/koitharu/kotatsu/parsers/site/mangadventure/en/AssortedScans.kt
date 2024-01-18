package org.koitharu.kotatsu.parsers.site.mangadventure.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.mangadventure.MangAdventureParser

@MangaSourceParser("ASSORTEDSCANS", "Assorted Scans", "en")
internal class AssortedScans(context: MangaLoaderContext) :
	MangAdventureParser(context, MangaSource.ASSORTEDSCANS, "assortedscans.com") {
	// tags that don't have any series and make the tests fail
	private val emptyTags = setOf(
		"Doujinshi", "Harem", "Hentai", "Mecha",
		"Shoujo Ai", "Shounen Ai", "Smut", "Yaoi"
	)

	override suspend fun getAvailableTags(): Set<MangaTag> =
		super.getAvailableTags().filterTo(HashSet()) { it.key !in emptyTags }
}
