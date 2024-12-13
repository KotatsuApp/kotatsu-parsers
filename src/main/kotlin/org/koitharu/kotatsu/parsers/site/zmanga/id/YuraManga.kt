package org.koitharu.kotatsu.parsers.site.zmanga.id

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zmanga.ZMangaParser

@Broken
@MangaSourceParser("YURAMANGA", "YuraManga", "id")
internal class YuraManga(context: MangaLoaderContext) :
	ZMangaParser(context, MangaParserSource.YURAMANGA, "www.yuramanga.my.id")

