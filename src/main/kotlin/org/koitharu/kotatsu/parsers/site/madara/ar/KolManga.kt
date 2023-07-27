package org.koitharu.kotatsu.parsers.site.madara.ar


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KOLMANGA", "KolManga", "ar")
internal class KolManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.KOLMANGA, "kolmanga.com")
