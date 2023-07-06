package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("MANGASORIGINES", "Mangas Origines", "fr")
internal class MangasOrigines(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGASORIGINES, "mangas-origines.fr") {

	override val datePattern = "dd/MM/yyyy"
	override val sourceLocale: Locale = Locale.FRENCH
}
