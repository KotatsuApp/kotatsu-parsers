package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.*

@Broken
@MangaSourceParser("GEKKOUSCANS", "GekkouScans", "pt", ContentType.HENTAI)
internal class GekkouScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.GEKKOUSCANS, "gekkou.site") {
	override val sourceLocale: Locale = Locale.ENGLISH
}
