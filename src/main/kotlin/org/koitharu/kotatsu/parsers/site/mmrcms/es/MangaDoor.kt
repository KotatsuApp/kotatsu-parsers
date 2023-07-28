package org.koitharu.kotatsu.parsers.site.mmrcms.es


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser
import java.util.Locale


@MangaSourceParser("MANGADOOR", "Manga Door", "es")
internal class MangaDoor(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaSource.MANGADOOR, "mangadoor.com") {


	override val sourceLocale: Locale = Locale.ENGLISH

	override val selectState = "dt:contains(Estado)"
	override val selectAlt = "dt:contains(Otros nombres)"
	override val selectAut = "dt:contains(Autor(es))"
	override val selectTag = "dt:contains(Categorías)"
}
