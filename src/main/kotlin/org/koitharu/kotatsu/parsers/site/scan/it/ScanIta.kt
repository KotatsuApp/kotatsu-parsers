package org.koitharu.kotatsu.parsers.site.scan.it

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.scan.ScanParser

@MangaSourceParser("SCANITA", "ScanIta.org", "it")
internal class ScanIta(context: MangaLoaderContext) :
	ScanParser(context, MangaSource.SCANITA, "scanita.org")
