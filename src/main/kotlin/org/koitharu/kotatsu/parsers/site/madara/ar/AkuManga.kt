package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("AKUMANGA", "AkuManga", "ar")
internal class AkuManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.AKUMANGA, "akumanga.com") {
	override val sourceLocale: Locale = Locale.ENGLISH
}
