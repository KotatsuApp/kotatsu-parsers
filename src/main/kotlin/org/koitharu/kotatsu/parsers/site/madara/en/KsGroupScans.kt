package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KSGROUPSCANS", "Ks Group Scans", "en")
internal class KsGroupScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.KSGROUPSCANS, "ksgroupscans.com")
