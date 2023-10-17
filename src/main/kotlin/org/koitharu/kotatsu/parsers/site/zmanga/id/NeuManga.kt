package org.koitharu.kotatsu.parsers.site.zmanga.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.zmanga.ZMangaParser

@MangaSourceParser("NEU_MANGA", "NeuManga.net", "id")
internal class NeuManga(context: MangaLoaderContext) :
	ZMangaParser(context, MangaSource.NEU_MANGA, "neumanga.net")
