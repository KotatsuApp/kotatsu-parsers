package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TEENMANHUA", "Teen Manhua", "en")
internal class TeenManhua(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TEENMANHUA, "teenmanhua.com") {
	override val datePattern = "dd/MM/yyyy"
}
