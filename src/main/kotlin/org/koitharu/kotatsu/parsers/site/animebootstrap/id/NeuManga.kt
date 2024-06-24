package org.koitharu.kotatsu.parsers.site.animebootstrap.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.animebootstrap.AnimeBootstrapParser

@MangaSourceParser("NEUMANGA", "NeuManga.xyz", "id")
internal class NeuManga(context: MangaLoaderContext) :
	AnimeBootstrapParser(context, MangaParserSource.NEUMANGA, "neumanga.xyz")
