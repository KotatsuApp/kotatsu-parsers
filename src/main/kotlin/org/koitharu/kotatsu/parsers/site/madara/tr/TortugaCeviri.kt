package org.koitharu.kotatsu.parsers.site.madara.tr


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("TORTUGACEVIRI", "Tortuga Ceviri", "tr")
internal class TortugaCeviri(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TORTUGACEVIRI, "tortuga-ceviri.com")
