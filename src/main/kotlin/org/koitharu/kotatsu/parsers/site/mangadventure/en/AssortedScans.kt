package org.koitharu.kotatsu.parsers.site.mangadventure.en

import androidx.collection.ArraySet
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.mangadventure.MangAdventureParser

@MangaSourceParser("ASSORTEDSCANS", "AssortedScans", "en")
internal class AssortedScans(context: MangaLoaderContext) :
	MangAdventureParser(context, MangaParserSource.ASSORTEDSCANS, "assortedscans.com") {
	// tags that don't have any series and make the tests fail
	private val emptyTags = setOf(
		"Doujinshi", "Harem", "Hentai", "Mecha",
		"Shoujo Ai", "Shounen Ai", "Smut", "Yaoi",
	)

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val tags = super.getAvailableTags()
		return tags.filterNotTo(ArraySet(tags.size)) { it.key in emptyTags }
	}
}
