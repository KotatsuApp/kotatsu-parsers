package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("GALAXYACTION", "GalaxyAction", "ar")
internal class GalaxyAction(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.GALAXYACTION, "galaxyaction.site", pageSize = 20, searchPageSize = 10)
