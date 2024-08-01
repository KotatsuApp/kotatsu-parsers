package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("YAOIHUB", "YaoiHub", "en", ContentType.HENTAI)
internal class YaoiHub(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.YAOIHUB, "yaoihub.com")
