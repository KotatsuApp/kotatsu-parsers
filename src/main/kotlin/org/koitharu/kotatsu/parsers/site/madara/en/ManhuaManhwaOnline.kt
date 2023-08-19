package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHUAMANHWAONLINE", "Manhua Manhwa Online", "en")
internal class ManhuaManhwaOnline(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHUAMANHWAONLINE, "manhuamanhwa.online", 10)
