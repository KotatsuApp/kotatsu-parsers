package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHUASCANINFO", "Manhua Scan .Info", "en")
internal class ManhuaScanInfo(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHUASCANINFO, "manhuascan.info", 10) {
	override val postreq = true
}
