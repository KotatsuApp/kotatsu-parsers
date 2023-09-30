package org.koitharu.kotatsu.parsers.site.nepnep.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.nepnep.NepnepParser

@MangaSourceParser("MANGASEE", "Manga See", "en")
internal class MangaSee(context: MangaLoaderContext) :
	NepnepParser(context, MangaSource.MANGASEE, "mangasee123.com")
