package org.koitharu.kotatsu.parsers.site.zeistmanga.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("ROCKSMANGA_COM", "RocksManga.com", "ar")
internal class RocksManga(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaSource.ROCKSMANGA_COM, "www.rocks-manga.com")
