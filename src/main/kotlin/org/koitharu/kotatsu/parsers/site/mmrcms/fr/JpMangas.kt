package org.koitharu.kotatsu.parsers.site.mmrcms.fr


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MmrcmsParser
import java.util.Locale


@MangaSourceParser("JPMANGAS", "JpMangas", "fr")
internal class JpMangas(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaSource.JPMANGAS, "jpmangas.xyz") {


	override val sourceLocale: Locale = Locale.ENGLISH
}
