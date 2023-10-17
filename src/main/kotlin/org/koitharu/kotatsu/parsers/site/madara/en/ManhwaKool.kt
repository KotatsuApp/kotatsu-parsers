package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWAKOOL", "ManhwaKool", "en")
internal class ManhwaKool(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWAKOOL, "manhwakool.com", pageSize = 10)
