package org.koitharu.kotatsu.parsers.site.pizzareader.it

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.pizzareader.PizzaReaderParser

@MangaSourceParser("LUPITEAM", "LupiTeam", "it")
internal class LupiTeam(context: MangaLoaderContext) :
	PizzaReaderParser(context, MangaSource.LUPITEAM, "lupiteam.net")
