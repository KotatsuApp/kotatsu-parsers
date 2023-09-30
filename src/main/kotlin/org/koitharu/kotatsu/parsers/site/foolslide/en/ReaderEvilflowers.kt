package org.koitharu.kotatsu.parsers.site.foolslide.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.foolslide.FoolSlideParser

@MangaSourceParser("READER_EVILFLOWERS", "Evil Flowers", "en")
internal class ReaderEvilflowers(context: MangaLoaderContext) :
	FoolSlideParser(context, MangaSource.READER_EVILFLOWERS, "reader.evilflowers.com")
