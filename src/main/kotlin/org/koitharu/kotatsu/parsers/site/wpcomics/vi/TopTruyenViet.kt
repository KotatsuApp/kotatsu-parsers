package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.Broken

@Broken
@MangaSourceParser("TOPTRUYENVIET", "TopTruyen.pro", "vi")
internal class TopTruyenViet(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.TOPTRUYENVIET, "www.toptruyen68.pro", 36)
