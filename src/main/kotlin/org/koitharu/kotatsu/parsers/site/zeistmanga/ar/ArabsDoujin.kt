package org.koitharu.kotatsu.parsers.site.zeistmanga.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser
import org.koitharu.kotatsu.parsers.Broken

@Broken("Original site closed")
@MangaSourceParser("ARABSDOUJIN", "ArabsDoujin", "ar", ContentType.HENTAI)
internal class ArabsDoujin(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.ARABSDOUJIN, "www.arabsdoujin.online")
