package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("HWAGO", "Hwago", "id")
internal class Hwago(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HWAGO, "hwago.id", 10) {
	override val listUrl = "komik/"
	override val tagPrefix = "genre/"
	override val sourceLocale: Locale = Locale.ENGLISH
}
