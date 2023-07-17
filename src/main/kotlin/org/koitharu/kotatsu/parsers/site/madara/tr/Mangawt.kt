package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAWT", "Mangawt", "tr")
internal class Mangawt(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGAWT, "mangawt.com")
