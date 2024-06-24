package org.koitharu.kotatsu.parsers.site.zeistmanga.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("ASUPANKOMIK", "AsupanKomik", "id")
internal class AsupanKomik(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.ASUPANKOMIK, "www.asupankomik.my.id")
