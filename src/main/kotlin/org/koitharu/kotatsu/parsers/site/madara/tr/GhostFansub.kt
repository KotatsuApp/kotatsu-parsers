package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GHOSTFANSUB", "Ghost Fansub", "tr")
internal class GhostFansub(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.GHOSTFANSUB, "ghostfansub.online", 18)
// you now need to log in to access content
