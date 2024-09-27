package org.koitharu.kotatsu.parsers.site.mmrcms.fr

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser
import java.util.*

@Broken
@MangaSourceParser("MANGA_SCAN", "MangaScan", "fr")
internal class MangaScan(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaParserSource.MANGA_SCAN, "mangascan-fr.net") {
	override val imgUpdated = ".jpg"
	override val sourceLocale: Locale = Locale.ENGLISH
}
