package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("PRISMA_HENTAI", "Prisma hentai", "pt")
internal class Prismahentai(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.PRISMA_HENTAI, "prismahentai.com", 18) {

	override val datePattern = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale("pt", "PT")
}
