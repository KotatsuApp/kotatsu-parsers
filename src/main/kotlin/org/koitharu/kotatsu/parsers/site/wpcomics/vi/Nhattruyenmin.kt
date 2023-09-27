package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser

@MangaSourceParser("NHATTRUYENMIN", "Nhattruyen Min", "vi")
internal class Nhattruyenmin(context: MangaLoaderContext) :
	WpComicsParser(context, MangaSource.NHATTRUYENMIN, "nhattruyenmin.com")
