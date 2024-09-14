package org.koitharu.kotatsu.parsers.site.zeistmanga.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("ULASCOMIC", "UlasComic", "id")
internal class UlasComic(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.ULASCOMIC, "www.ulascomic.xyz")
