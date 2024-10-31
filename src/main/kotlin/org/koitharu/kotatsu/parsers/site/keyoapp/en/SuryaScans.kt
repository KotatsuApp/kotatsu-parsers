package org.koitharu.kotatsu.parsers.site.keyoapp.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.keyoapp.KeyoappParser

@MangaSourceParser("SURYASCANS", "GenzToon", "en")
internal class SuryaScans(context: MangaLoaderContext) :
	KeyoappParser(context, MangaParserSource.SURYASCANS, "genzupdates.com")
