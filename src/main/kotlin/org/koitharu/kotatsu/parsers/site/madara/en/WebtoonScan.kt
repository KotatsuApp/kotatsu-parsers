package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WEBTOONSCAN", "Webtoon Scan", "en", ContentType.HENTAI)
internal class WebtoonScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WEBTOONSCAN, "webtoonscan.com", 20)
