package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("NOBLESSETRANSLATIONS", "Noblesse Translations", "es")
internal class NoblesseTranslations(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.NOBLESSETRANSLATIONS, "www.noblessetranslations.com") {

	override val datePattern = "d MMMM, yyyy"
}
