package org.koitharu.kotatsu.parsers.site.zeistmanga.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("KOMIKREALM", "KomikRealm", "id")
internal class KomikRealm(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.KOMIKREALM, "www.komikrealm.my.id")
