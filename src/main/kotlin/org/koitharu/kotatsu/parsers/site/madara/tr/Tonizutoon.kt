package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TONIZUTOON", "TonizuToon", "tr", ContentType.HENTAI)
internal class Tonizutoon(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TONIZUTOON, "tonizutoon.com")
