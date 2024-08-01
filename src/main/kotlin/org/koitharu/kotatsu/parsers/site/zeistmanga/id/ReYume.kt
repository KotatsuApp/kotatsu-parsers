package org.koitharu.kotatsu.parsers.site.zeistmanga.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("REYUME", "ReYume", "id")
internal class ReYume(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.REYUME, "www.re-yume.my.id")
