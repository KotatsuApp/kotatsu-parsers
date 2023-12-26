package org.koitharu.kotatsu.parsers.site.gattsu.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.gattsu.GattsuParser

@MangaSourceParser("HENTAISEASON", "HentaiSeason", "pt", ContentType.HENTAI)
internal class HentaiSeason(context: MangaLoaderContext) :
	GattsuParser(context, MangaSource.HENTAISEASON, "hentaiseason.com")
