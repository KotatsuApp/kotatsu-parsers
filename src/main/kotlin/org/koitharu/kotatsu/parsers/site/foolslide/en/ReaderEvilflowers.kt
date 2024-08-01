package org.koitharu.kotatsu.parsers.site.foolslide.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.foolslide.FoolSlideParser

@Broken
@MangaSourceParser("READER_EVILFLOWERS", "Evil Flowers", "en")
internal class ReaderEvilflowers(context: MangaLoaderContext) :
	FoolSlideParser(context, MangaParserSource.READER_EVILFLOWERS, "reader.evilflowers.com")
