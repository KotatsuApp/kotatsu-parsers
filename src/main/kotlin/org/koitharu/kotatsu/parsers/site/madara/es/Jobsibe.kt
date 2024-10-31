package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("JOBSIBE", "Jobsibe", "es")
internal class Jobsibe(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.JOBSIBE, "jobsibe.com") {
	override val datePattern = "dd/MM"
}
