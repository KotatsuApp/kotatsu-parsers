package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ZINMANGA_TOP", "Zin Manga .Top", "en")
internal class ZinMangaTop(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ZINMANGA_TOP, "zinmanga.top", 20)
