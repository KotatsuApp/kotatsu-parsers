package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("YAOISCAN", "Yaoi Scan", "en", ContentType.HENTAI)
internal class YaoiScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.YAOISCAN, "yaoiscan.com", 20)
