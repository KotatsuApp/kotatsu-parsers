package org.koitharu.kotatsu.parsers.site.zeistmanga.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("TEMAKIMANGAS", "TemakiMangas", "pt")
internal class TemakiMangas(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.TEMAKIMANGAS, "www.temakimangas.xyz") {
	override val availableStates: Set<MangaState> = emptySet()
	override suspend fun getAvailableTags(): Set<MangaTag> = emptySet()
}


