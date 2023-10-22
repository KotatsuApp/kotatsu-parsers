package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("NITROMANGA", "NitroManga", "en")
internal class NitroManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.NITROMANGA, "nitromanga.com", pageSize = 18)
