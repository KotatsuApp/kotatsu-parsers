package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("WORLDMANHWAS", "Worldmanhwas", "id")
internal class Worldmanhwas(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WORLDMANHWAS, "worldmanhwas.bar", 10) {

	override val tagPrefix = "komik-genre/"
	override val datePattern = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
}
