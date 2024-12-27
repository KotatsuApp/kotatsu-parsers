package org.koitharu.kotatsu.parsers.site.madara.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RUAHAPCHANHDAY", "Rùa Hấp Chanh Dây", "vi")
internal class RuaHapChanhDay(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.RUAHAPCHANHDAY, "ruahapchanhday.com", 30) {
	override val datePattern = "dd/MM/yyyy"
}
