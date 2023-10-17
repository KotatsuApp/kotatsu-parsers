package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAUPDATESTOP", "MangaUpdates.top", "en")
internal class MangaUpdatesTop(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAUPDATESTOP, "mangaupdates.top", 10) {
	override val postReq = true
}
