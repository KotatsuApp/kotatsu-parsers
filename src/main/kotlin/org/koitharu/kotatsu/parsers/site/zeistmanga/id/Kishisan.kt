package org.koitharu.kotatsu.parsers.site.zeistmanga.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("KISHISAN", "Kishisan", "id")
internal class Kishisan(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.KISHISAN, "www.kishisan.site")
