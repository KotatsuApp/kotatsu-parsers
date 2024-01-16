package org.koitharu.kotatsu.parsers.site.manga18.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.manga18.Manga18Parser

@MangaSourceParser("COMIC1000", "Comic1000", "en")
internal class Comic1000(context: MangaLoaderContext) :
	Manga18Parser(context, MangaSource.COMIC1000, "comic1000.com")
