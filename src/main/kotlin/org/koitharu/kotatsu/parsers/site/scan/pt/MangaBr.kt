package org.koitharu.kotatsu.parsers.site.scan.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.scan.ScanParser

@MangaSourceParser("MANGABR", "MangaBr", "pt")
internal class MangaBr(context: MangaLoaderContext) :
	ScanParser(context, MangaSource.MANGABR, "mangabr.net")
