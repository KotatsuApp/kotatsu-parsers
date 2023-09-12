package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SETSUSCANS", "Setsu Scans", "en")
internal class SetsuScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SETSUSCANS, "setsuscans.com")
