package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGATXUNOFFICIAL", "MangaEmpress", "en")
internal class MangaTxUnofficial(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGATXUNOFFICIAL, "mangaempress.com")
