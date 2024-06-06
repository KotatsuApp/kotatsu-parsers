package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("DEMONSECT", "DemonSect", "pt")
internal class DemonSect(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.DEMONSECT, "dsectcomics.org", 10)
