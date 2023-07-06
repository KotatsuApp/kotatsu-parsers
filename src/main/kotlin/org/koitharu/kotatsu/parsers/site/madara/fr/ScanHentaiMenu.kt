package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("SCANHENTAIMENU", "Scan Hentai Menu", "fr")
internal class ScanHentaiMenu(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SCANHENTAIMENU, "scan.hentai.menu") {

	override val isNsfwSource = true
	override val datePattern = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale.FRENCH
}
