package org.koitharu.kotatsu.parsers.site.foolslide.pl


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.foolslide.FoolSlideParser


@MangaSourceParser("ONEPIECENAKAMA", "Onepiecenakama", "pl")
internal class Onepiecenakama(context: MangaLoaderContext) :
	FoolSlideParser(context, MangaSource.ONEPIECENAKAMA, "reader.onepiecenakama.pl")
