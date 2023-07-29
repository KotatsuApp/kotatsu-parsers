package org.koitharu.kotatsu.parsers.site.madtheme.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madtheme.MadthemeParser


@MangaSourceParser("TRUEMANGA", "True Manga", "en")
internal class TrueManga(context: MangaLoaderContext) :
	MadthemeParser(context, MangaSource.TRUEMANGA, "truemanga.com")
