package org.koitharu.kotatsu.parsers.site.guya.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.guya.GuyaParser

@MangaSourceParser("HACHIRUMI", "Hachirumi", "en", ContentType.HENTAI)
internal class Hachirumi(context: MangaLoaderContext) :
	GuyaParser(context, MangaParserSource.HACHIRUMI, "hachirumi.com")
