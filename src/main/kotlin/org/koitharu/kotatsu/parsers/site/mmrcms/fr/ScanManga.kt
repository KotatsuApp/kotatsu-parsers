package org.koitharu.kotatsu.parsers.site.mmrcms.fr


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser
import java.util.Locale


@MangaSourceParser("SCANMANGA", "Scan Manga", "fr")
internal class ScanManga(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaSource.SCANMANGA, "scan-manga.me") {

	override val imgUpdated = ".jpg"
	override val sourceLocale: Locale = Locale.ENGLISH
}
