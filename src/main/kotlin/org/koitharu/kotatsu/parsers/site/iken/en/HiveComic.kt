package org.koitharu.kotatsu.parsers.site.iken.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.iken.IkenParser
import org.koitharu.kotatsu.parsers.Broken

@Broken("Need to fix getPages")
@MangaSourceParser("HIVECOMIC", "HiveComic", "en")
internal class HiveComic(context: MangaLoaderContext) :
	IkenParser(context, MangaParserSource.HIVECOMIC, "hivecomic.com", 18)