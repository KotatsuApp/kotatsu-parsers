package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.Broken

@Broken
@MangaSourceParser("TECNOPROJECTS", "TecnoProjects", "es")
internal class TecnoProjects(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.TECNOPROJECTS, "tecnoprojects.xyz") {
	override val datePattern = "dd 'de' MMMM 'de' yyyy"
}
