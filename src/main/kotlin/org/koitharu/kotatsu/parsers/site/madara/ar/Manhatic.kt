package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("MANHATIC", "Manhatic", "ar")
internal class Manhatic(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHATIC, "manhatic.com") {

	override val isNsfwSource = true
	override val sourceLocale: Locale = Locale.ENGLISH
}
