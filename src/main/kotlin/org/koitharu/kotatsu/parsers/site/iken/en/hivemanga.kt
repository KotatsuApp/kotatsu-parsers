package org.koitharu.kotatsu.parsers.site.iken.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.iken.IkenParser

@MangaSourceParser("HIVEMANGA", "Hivemanga", "en")
internal class Hivemanga(context: MangaLoaderContext) :
	IkenParser(context, MangaParserSource.HIVEMANGA, "hivecomic.com") {
	override val selectPages = "main section img"
}
