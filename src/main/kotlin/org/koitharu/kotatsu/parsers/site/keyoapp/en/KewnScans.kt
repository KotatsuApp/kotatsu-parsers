package org.koitharu.kotatsu.parsers.site.keyoapp.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.keyoapp.KeyoappParser

@MangaSourceParser("KEWNSCANS", "KewnScans", "en")
internal class KewnScans(context: MangaLoaderContext) :
	KeyoappParser(context, MangaParserSource.KEWNSCANS, "kewnscans.org")
