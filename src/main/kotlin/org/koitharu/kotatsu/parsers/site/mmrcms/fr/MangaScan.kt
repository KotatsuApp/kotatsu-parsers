package org.koitharu.kotatsu.parsers.site.mmrcms.fr


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MmrcmsParser
import java.util.Locale


@MangaSourceParser("MANGA_SCAN", "Manga-Scan", "fr")
internal class MangaScan(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaSource.MANGA_SCAN, "manga-scan.co") {

	override val imgUpdated = ".jpg"
	override val sourceLocale: Locale = Locale.ENGLISH
}
