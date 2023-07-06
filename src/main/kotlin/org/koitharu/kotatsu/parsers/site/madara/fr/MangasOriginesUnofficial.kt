package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("MANGASORIGINESUNOFFICIAL", "Mangas Origines ( unofficial )", "fr")
internal class MangasOriginesUnofficial(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGASORIGINESUNOFFICIAL, "mangas-origines.xyz") {


	override val datePattern = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale.FRENCH

}
