package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("DECADENCESCANS", "Decadence Scans", "en", ContentType.HENTAI)
internal class DecadenceScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.DECADENCESCANS, "reader.decadencescans.com", 10)
