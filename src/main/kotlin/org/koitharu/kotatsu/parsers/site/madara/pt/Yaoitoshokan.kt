package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("YAOITOSHOKAN", "Yaoitoshokan", "pt")
internal class Yaoitoshokan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.YAOITOSHOKAN, "www.yaoitoshokan.net", 18) {

	override val isNsfwSource = true
	override val tagPrefix = "genero/"
	override val datePattern: String = "d MMM yyyy"
}
