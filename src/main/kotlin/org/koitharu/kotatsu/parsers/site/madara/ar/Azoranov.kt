package org.koitharu.kotatsu.parsers.site.madara.ar


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("AZORANOV", "Azoranov", "ar")
internal class Azoranov(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.AZORANOV, "azoranov.com", pageSize = 10) {

	override val tagPrefix = "novel-genre/"
}
