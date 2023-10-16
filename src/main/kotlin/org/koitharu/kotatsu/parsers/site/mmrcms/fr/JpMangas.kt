package org.koitharu.kotatsu.parsers.site.mmrcms.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser
import java.util.*

@MangaSourceParser("JPMANGAS", "Jp Mangas", "fr")
internal class JpMangas(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaSource.JPMANGAS, "jpmangas.xyz") {
	override val sourceLocale: Locale = Locale.ENGLISH
}
