package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGATXUNOFFICIAL", "Manga-Tx .Com", "en")
internal class MangaTxUnofficial(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGATXUNOFFICIAL, "manga-tx.com")
