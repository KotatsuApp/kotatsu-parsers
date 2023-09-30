package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LUXMANGA", "Lux Manga", "en")
internal class LuxManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LUXMANGA, "luxmanga.net")
