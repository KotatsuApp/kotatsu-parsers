package org.koitharu.kotatsu.parsers.site.animebootstrap.id


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.animebootstrap.AnimeBootstrapParser


@MangaSourceParser("NEUMANGA", "NeuManga", "id")
internal class NeuManga(context: MangaLoaderContext) :
	AnimeBootstrapParser(context, MangaSource.NEUMANGA, "neumanga.xyz")
