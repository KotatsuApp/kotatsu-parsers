package org.koitharu.kotatsu.parsers.site.keyoapp.fr

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.keyoapp.KeyoappParser

@Broken
@MangaSourceParser("REAPERSCANS_FR", "ReaperScans.fr", "fr")
internal class ReaperScansFr(context: MangaLoaderContext) :
	KeyoappParser(context, MangaParserSource.REAPERSCANS_FR, "reaper-scans.fr")
